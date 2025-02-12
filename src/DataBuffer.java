
public class DataBuffer {

    public static final int NUM_BUFFERS   =   10;

    private int pos_receive, cnt_receive ;
    private int pos_process, cnt_process ;
    private Boolean dataReceive          ;
    private String[] packets             ;

    public DataBuffer(){

        pos_receive =     0 ;
        cnt_receive =     0 ;
        cnt_process =     0 ;
        pos_process =     0 ;
        dataReceive = false ;

        packets = new String[NUM_BUFFERS];

        for (int i=0; i<NUM_BUFFERS; i++) packets[i] = "";
    }

    public void add(String packet){

        int length = packet.length();

        if (length > 0 && packet != null) {

            packets[pos_receive] = packet;
            pos_receive++;
            if (pos_receive >= NUM_BUFFERS) pos_receive = 0;
            cnt_receive++;
            dataReceive = true;
        }
    }

    public String getData(){

        if (!dataReceive) return null;

        String dataPacket = packets[pos_process];

        pos_process++;
        if (pos_process >= NUM_BUFFERS) pos_process = 0;

        if (pos_process == pos_receive) dataReceive = false;

        cnt_process++;

        return dataPacket;
    }

    public String getStatus(){

        String status = cnt_process + "/" + cnt_receive;

        return status;
    }
}
