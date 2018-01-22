package base.tiffany.myadblock;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.util.Log;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import static base.tiffany.myadblock.MainVpnService.VPN_STATUS_RUNNING;

public class VpnThread implements Runnable {

    private Thread thread = null;
    private final String TAG = this.getClass().getSimpleName();
    private final Notify mNotify;
    private final VpnService mVpnService;

    final ArrayList<InetAddress> upstreamDnsServers = new ArrayList<>();

    private FileDescriptor mInterruptFd = null;
    private FileDescriptor mBlockFd = null;

    public VpnThread(MainVpnService vpnService, Notify notify) {
        mNotify = notify;
        mVpnService = vpnService;

    }

    public interface Notify {
        void run(int value);
    }

    public void startThread() {
        thread = new Thread(this, "MyVpnThread");
        thread.start();
        Log.i(getClass().getSimpleName(), "Vpn Thread Started");
    }

    public void stopThread() {
        Log.i(TAG, "Stopping Vpn Thread");

        if (thread != null) thread.interrupt();
        mInterruptFd = FileHelper.closeOrWarn(mInterruptFd, TAG, "stopThread: Could not close interruptFd");

        try {
            if (thread != null) thread.join(2000);
        } catch (InterruptedException e) {
            Log.w(TAG, "stopThread: Interrupted while joining thread", e);
        }
        if (thread != null && thread.isAlive()) {
            Log.w(TAG, "stopThread: Could not kill VPN thread, it is still alive");
        } else {
            thread = null;
            Log.i(TAG, "Vpn Thread stopped");
        }
    }

    @Override
    public void run() {
        if (mNotify != null) {
            mNotify.run(MainVpnService.VPN_STATUS_STARTING);
        }

        while (true) {
            try {
                runVpn();
                Log.i(TAG, "Told to stop");
                mNotify.run(MainVpnService.VPN_STATUS_STOPPING);
                break;
            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                e.printStackTrace();
                Log.i(TAG, "Error in connecting. Cannot connect");
            }
        }
    }

    private void runVpn() throws InterruptedException, ErrnoException, IOException, VpnNetworkException {
        // Allocate the buffer for a single packet.
        byte[] packet = new byte[32767];

        // A pipe we can interrupt the poll() call with by closing the interruptFd end
        FileDescriptor[] pipes = Os.pipe();
        mInterruptFd = pipes[0];
        mBlockFd = pipes[1];

        // Authenticate and configure the virtual network interface.
        try (ParcelFileDescriptor pfd = configure()) {
            // Read and write views of the tun device
            FileInputStream inputStream = new FileInputStream(pfd.getFileDescriptor());
            FileOutputStream outFd = new FileOutputStream(pfd.getFileDescriptor());

            // Now we are connected. Set the flag and show the message.
            if (mNotify != null)
                mNotify.run(VPN_STATUS_RUNNING);

            // We keep forwarding packets till something goes wrong.
            while (readEachPacket(inputStream, outFd, packet)) ;
        } finally {
            mBlockFd = FileHelper.closeOrWarn(mBlockFd, TAG, "runVpn: Could not close blockFd");
        }
    }

    private ParcelFileDescriptor configure() throws VpnNetworkException {
        Log.i(TAG, "Configuring" + this);
        Configuration config = FileHelper.loadCurrentSettings(mVpnService);

        Set<InetAddress> dnsServers = getDnsServers(mVpnService);
        Log.i(TAG, "Got DNS servers = " + dnsServers);

        VpnService.Builder builder = mVpnService.new Builder();

        String format = null;
        for (String prefix : new String[]{"192.0.2", "198.51.100", "203.0.113"}) {
            try {
                builder.addAddress(prefix + ".1", 24);
            } catch (IllegalArgumentException e) {
                continue;
            }

            format = prefix + ".%d";
            break;
        }

        byte[] ipv6Template = new byte[]{32, 1, 13, (byte) (184 & 0xFF), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};

        if (hasIpV6Servers(config, dnsServers)) {
            try {
                InetAddress addr = Inet6Address.getByAddress(ipv6Template);
                Log.d(TAG, "configure: Adding IPv6 address" + addr);
                builder.addAddress(addr, 120);
            } catch (Exception e) {
                e.printStackTrace();

                ipv6Template = null;
            }
        } else {
            ipv6Template = null;
        }

        if (format == null) {
            Log.w(TAG, "configure: Could not find a prefix to use, directly using DNS servers");
            builder.addAddress("192.168.50.1", 24);
        }

        // Add configured DNS servers
        upstreamDnsServers.clear();
        if (config.dnsServers.enabled) {
            for (Configuration.Item item : config.dnsServers.items) {
                if (item.state == item.STATE_ALLOW) {
                    try {
                        newDNSServer(builder, format, ipv6Template, InetAddress.getByName(item.location));
                    } catch (Exception e) {
                        Log.e(TAG, "configure: Cannot add custom DNS server", e);
                    }
                }
            }
        }

        // Add all knows DNS servers
        for (InetAddress addr : dnsServers) {
            try {
                newDNSServer(builder, format, ipv6Template, addr);
            } catch (Exception e) {
                Log.e(TAG, "configure: Cannot add server:", e);
            }
        }

        builder.setBlocking(true);
        builder.allowBypass();

        builder.allowFamily(OsConstants.AF_INET);
        builder.allowFamily(OsConstants.AF_INET6);

        configurePackages(builder, config);

        ParcelFileDescriptor pfd = builder
                .setSession("MyAdBlock")
                .setConfigureIntent(
                        PendingIntent.getActivity(mVpnService, 1, new Intent(mVpnService, MainActivity.class),
                                PendingIntent.FLAG_CANCEL_CURRENT)).establish();

        Log.i(TAG, "All Configured");
        return pfd;
    }

    private boolean readEachPacket(FileInputStream inputStream, FileOutputStream outFd, byte[] packet) throws IOException, ErrnoException, InterruptedException, VpnNetworkException {
        // TODO read the packet
        // TODO Check to see if the DNS server is blocked using a ruleDatabase
        return false;
    }

    private void newDNSServer(VpnService.Builder builder, String format, byte[] ipv6Template, InetAddress addr) throws UnknownHostException {
        if (addr instanceof Inet6Address && ipv6Template == null || addr instanceof Inet4Address && format == null) {
            Log.i(TAG, "newDNSServer: Ignoring DNS server " + addr);
        } else if (addr instanceof Inet4Address) {
            upstreamDnsServers.add(addr);
            String alias = String.format(format, upstreamDnsServers.size() + 1);
            Log.i(TAG, "configure: Adding DNS Server " + addr + " as " + alias);
            builder.addDnsServer(alias);
            builder.addRoute(alias, 32);
        } else if (addr instanceof Inet6Address) {
            upstreamDnsServers.add(addr);
            ipv6Template[ipv6Template.length - 1] = (byte) (upstreamDnsServers.size() + 1);
            InetAddress i6addr = Inet6Address.getByAddress(ipv6Template);
            Log.i(TAG, "configure: Adding DNS Server " + addr + " as " + i6addr);
            builder.addDnsServer(i6addr);
        }
    }

    private void configurePackages(VpnService.Builder builder, Configuration config) {
        Set<String> allowOnVpn = new HashSet<>();
        Set<String> doNotAllowOnVpn = new HashSet<>();

        config.whitelist.resolve(mVpnService.getPackageManager(), allowOnVpn, doNotAllowOnVpn);

        if (config.whitelist.defaultMode == Configuration.Whitelist.DEFAULT_MODE_NOT_ON_VPN) {
            for (String app : allowOnVpn) {
                try {
                    Log.d(TAG, "configure: Allowing " + app + " to use the DNS VPN");
                    builder.addAllowedApplication(app);
                } catch (Exception e) {
                    Log.w(TAG, "configure: Cannot disallow", e);
                }
            }
        } else {
            for (String app : doNotAllowOnVpn) {
                try {
                    Log.d(TAG, "configure: Disallowing " + app + " from using the DNS VPN");
                    builder.addDisallowedApplication(app);
                } catch (Exception e) {
                    Log.w(TAG, "configure: Cannot disallow", e);
                }
            }
        }
    }

    private boolean hasIpV6Servers(Configuration config, Set<InetAddress> dnsServers) {
        if (!config.ipV6Support)
            return false;

        if (config.dnsServers.enabled) {
            for (Configuration.Item item : config.dnsServers.items) {
                if (item.state == Configuration.Item.STATE_ALLOW && item.location.contains(":"))
                    return true;
            }
        }
        for (InetAddress inetAddress : dnsServers) {
            if (inetAddress instanceof Inet6Address)
                return true;
        }

        return false;
    }

    private static Set<InetAddress> getDnsServers(Context context) throws VpnNetworkException {
        Set<InetAddress> out = new HashSet<>();
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(VpnService.CONNECTIVITY_SERVICE);
        // Seriously, Android? Seriously?
        NetworkInfo activeInfo = cm.getActiveNetworkInfo();
        if (activeInfo == null)
            throw new VpnNetworkException("No DNS Server");

        for (Network nw : cm.getAllNetworks()) {
            NetworkInfo ni = cm.getNetworkInfo(nw);
            if (ni == null || !ni.isConnected() || ni.getType() != activeInfo.getType()
                    || ni.getSubtype() != activeInfo.getSubtype())
                continue;
            for (InetAddress address : cm.getLinkProperties(nw).getDnsServers())
                out.add(address);
        }
        return out;
    }

    static class VpnNetworkException extends Exception {
        VpnNetworkException(String s) {
            super(s);
        }

        VpnNetworkException(String s, Throwable t) {
            super(s, t);
        }

    }


}
