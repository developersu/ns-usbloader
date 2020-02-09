package nsusbloader.Utilities;

public class RcmSmash {

    private static final boolean supported;

    static {
        supported = JNIRcmLoader.load();
    }

    private RcmSmash(){}

    public static native int smashLinux(final int bus_id, final int device_addr);
    public static native int smashWindows();

    public static boolean isSupported() { return supported; }
}
