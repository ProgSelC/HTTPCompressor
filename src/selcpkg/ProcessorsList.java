package selcpkg;

import java.util.ArrayList;
import java.util.List;

public class ProcessorsList implements Processor {
	
    private List<Processor> list = new ArrayList<Processor>();
    
    public void add(Processor p) {
        list.add(p);
    }

    public byte[] process(byte[] data, List<String> headers) {
        byte[] res = data;
        
        for (Processor p : list)
             res = p.process(res, headers);
             
        return res;
    }
}
