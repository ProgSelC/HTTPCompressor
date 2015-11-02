package selcpkg;

import java.io.*;
import java.util.*;
import java.net.Socket;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class Client implements Runnable {
    private Socket socket;
    private FileManager fm;
    
    public Client(Socket socket, String path) {
        this.socket = socket;
        fm = new FileManager(path);
    }

    private void returnStatusCode(int code, OutputStream os) throws IOException {
        String msg = null;

        switch (code) {
            case 400:
                msg = "HTTP/1.1 400 Bad Request";
                break;
            case 404:
                msg = "HTTP/1.1 404 Not Found";
                break;
            case 500:
                msg = "HTTP/1.1 500 Internal Server Error";
                break;
        }

        byte[] resp = msg.concat("\r\n\r\n").getBytes();
        os.write(resp);
    }
    
    private byte[] getBinaryHeaders(List<String> headers) {
        StringBuilder res = new StringBuilder();

        for (String s : headers) 
            res.append(s);
            
        return res.toString().getBytes();
    }

    private void doGet(String url, OutputStream os) throws IOException {
        if ("/".equals(url))
            url = "/index.html";

        List<String> headers = new ArrayList<String>();
        headers.add("HTTP/1.1 200 OK\r\n");

        byte[] content = fm.get(url);

        if (content == null) {
            returnStatusCode(404, os);
            return;
        }

        ProcessorsList pl = new ProcessorsList();
        pl.add(new Compressor(6));
        pl.add(new Chunker(30)); // comment
        content = pl.process(content, headers);

        if (content == null) {
            returnStatusCode(500, os);
            return;
        }

        // uncomment next line
        // headers.add("Content-Length: " + content.length + "\r\n");
        headers.add("Connection: close\r\n\r\n");

        os.write(getBinaryHeaders(headers));
        os.write(content);
    }

    private void doPost(String boundary, byte[] dataBlock, OutputStream os) throws IOException {

        List<String> headers = new ArrayList<String>();
        headers.add("HTTP/1.1 200 OK\r\n");
        headers.add("Content-Transfer-Encoding:binary\r\n");
        headers.add("Content-Type:application/zip\r\n");
        headers.add("Content-Disposition:attachment; filename=\"myZip.zip\"\r\n");

        ProcessorsList pl = new ProcessorsList();
        pl.add(new Compressor(9));
        pl.add(new Chunker(30));

        List<FileObj> files = getUploadedFiles(dataBlock, boundary);


        byte[] content = makeZIP(files);

        content = pl.process(content, headers);

        if (content != null) {
            headers.add("Connection: close\r\n\r\n");
            os.write(getBinaryHeaders(headers));
            os.write(content);
        } else {
            byte[] resp = "HTTP/1.1 404 Not Found\r\n\r\n".getBytes();
            os.write(resp);
        }

    }


    private void process(byte[] request, OutputStream os) throws IOException {
        final String zipURL = "/upload";
        byte[] headerBlock = new byte[0];
        byte[] dataBlock = new byte[0];

        int idx = 0;
        for (int i = 0; i < request.length - 4; i++) {
            if (request[i] == (byte) '\r' && request[i + 1] == (byte) '\n' && idx == 0){
                idx = i;
            }
            if (request[i] == (byte) '\r' && request[i + 1] == (byte) '\n' && request[i + 2] == (byte) '\r' && request[i + 3] == (byte) '\n') {

                headerBlock = Arrays.copyOfRange(request, 0, i + 2);
                dataBlock = Arrays.copyOfRange(request, i + 4, request.length);
                break;
            }
        }

        System.out.println(new String(request));
        System.out.println("---------------------------------------------");

        byte[] firstLine = Arrays.copyOfRange(request, 0, idx);

        String[] parts = new String(firstLine).split(" ");

        if (parts.length != 3) {
            returnStatusCode(400, os);
            return;
        }

        String method = parts[0], url = parts[1], version = parts[2];

        if ((!version.equalsIgnoreCase("HTTP/1.0")) && (!version.equalsIgnoreCase("HTTP/1.1"))) {
            returnStatusCode(400, os);
            return;
        }
        if (method.equalsIgnoreCase("GET")) {
            doGet(url, os);

        } else if(method.equalsIgnoreCase("POST") && url.equals(zipURL)){
            String boundary = getBoundary(getHeaders(headerBlock).get("Content-Type"));
            doPost(boundary, dataBlock, os);
        } else{
            returnStatusCode(400, os);
            return;
        }

    }

    public void run() {
        try {
            InputStream is = socket.getInputStream();
            OutputStream os = socket.getOutputStream();

            ByteArrayOutputStream bs = new ByteArrayOutputStream();
            byte[] buf, temp;
            int len, b;

            try {
                len = is.available();
                buf = new byte[len];

                if (is.read(buf) > 0)
                    bs.write(buf);

                process(bs.toByteArray(), os);

            } finally {
                socket.close();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            return;
        }
    }

    public static String getBoundary(String content) {
        String result = "";
        if (content.contains("boundary=")) {
            result = content.substring(content.indexOf("boundary=") + 9, content.length());
        }
        return result;
    }

    public static HashMap<String, String> getHeaders(byte[] headerBlock) {
        HashMap<String, String> headers = new HashMap<String, String>();
        int blStart = 0; // start of an info block
        String header = "";
        String value = "";
        int i = 0;
        int rownum = 1;
        while (i < headerBlock.length - 1) {
            if (header.equals("") && headerBlock[i] == 58 && headerBlock[i + 1] == 32) { // ":
                // "
                if (rownum > 1) {
                    header = new String(Arrays.copyOfRange(headerBlock, blStart, i));
                    blStart = i + 2;
                }
                i += 2;
            }
            if (headerBlock[i] == 13 && headerBlock[i + 1] == 10) { // "\r\n"
                if (rownum > 1 && !header.equals("")) {
                    value = new String(Arrays.copyOfRange(headerBlock, blStart, i));
                    headers.put(header, value);
                    header = "";
                }
                rownum++;
                blStart = i + 2;
                i += 2;
            }
            i++;
        }
        return headers;
    }

    public static List<FileObj> getUploadedFiles(byte[] dataBlock, String boundary) {
        List<FileObj> files = new ArrayList<FileObj>();
        String data = new String(dataBlock);
        for (String block : data.split("--" + boundary)) {

            if (block.indexOf("\r\n\r\n") >= 0) {

                byte[] header = block.substring(0, block.indexOf("\r\n\r\n")).getBytes();
                byte[] body = block.substring(block.indexOf("\r\n\r\n") + 4, block.length()).getBytes();

                String contentInfo = getHeaders(header).get("Content-Disposition");
                String filename = "";
                for (String contentEntry : contentInfo.split("; ")) {
                    if (contentEntry.contains("filename")) {
                        filename = contentEntry.replace("filename=\"", "").replace("\"", "");
                        System.out.println(filename);
                        break;
                    }

                }
                if(!filename.equals("")){
                    files.add(new FileObj(filename, body));
                }
            }
        }
        return files;
    }

    public static byte[] makeZIP(List<FileObj> files) throws IOException {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ZipOutputStream zos = new ZipOutputStream(baos);

        for (FileObj file : files) {
            ZipEntry entry = new ZipEntry(file.getFilename());
            entry.setSize(file.getFilebody().length);
            zos.putNextEntry(entry);
            zos.write(file.getFilebody());
            zos.closeEntry();
        }
        zos.close();
        return baos.toByteArray();
    }
}
