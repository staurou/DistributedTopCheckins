package ssn.models;

public class MapperAddRemove {
    private boolean add;
    private String host;
    private int port;

    public MapperAddRemove() {
    }

    public MapperAddRemove(boolean add, String host, int port) {
        this.add = add;
        this.host = host;
        this.port = port;
    }

    public boolean isAdd() {
        return add;
    }

    public void setAdd(boolean add) {
        this.add = add;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }
}
