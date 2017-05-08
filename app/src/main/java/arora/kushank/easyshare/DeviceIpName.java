package arora.kushank.easyshare;

/**
 * Created by Password on 23-Mar-17.
 */
public class DeviceIpName {
    public String ipAddress;
    public String name;

    public DeviceIpName(String ipAddress, String name) {
        this.ipAddress=ipAddress;
        this.name=name;
    }

    @Override
    public String toString() {
        return ipAddress+"#"+name;
    }
}
