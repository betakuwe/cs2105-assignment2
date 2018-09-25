public class FileSender {
    private String host;
    private int port;
    private String src;
    private String dest;
    
    public static void main(String[] args) {
        if (args.length != 4) {
            System.err.println("Usage: FileSender <host> <port> <src> <dest>");
            System.exit(-1);
        }
        
        FileSender fileSender = new FileSender(args[0], Integer.parseInt(args[1]), args[2], args[3]);
        fileSender.run();
    }

    private FileSender(String host, int port, String src, String dest) {
        this.host = host;
        this.port = port;
        this.src = src;
        this.dest = dest;
    }
}
