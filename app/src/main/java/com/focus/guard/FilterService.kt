package com.focus.guard

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Local DNS-only VPN. Only DNS queries to the virtual address 10.77.0.2 enter the tunnel;
 * they are relayed to Cloudflare Family (1.1.1.3 / 1.0.0.3), which refuses adult and malware domains.
 * All other traffic bypasses the tunnel, so speed is unaffected.
 */
class FilterService : VpnService() {
    companion object { @Volatile var running = false }
    private var fd: ParcelFileDescriptor? = null
    private val upstream = listOf(byteArrayOf(1, 1, 1, 3), byteArrayOf(1, 0, 0, 3))

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        if (fd == null) {
            fd = Builder().setSession("Focus Guard").addAddress("10.77.0.1", 32)
                .addDnsServer("10.77.0.2").addRoute("10.77.0.2", 32).setBlocking(true).establish()
            val d = fd
            if (d != null) { running = true; Thread { loop(d) }.start() }
        }
        return START_STICKY
    }

    override fun onRevoke() { stopSelf() }
    override fun onDestroy() { running = false; fd?.close(); fd = null }

    private fun loop(d: ParcelFileDescriptor) {
        val inp = FileInputStream(d.fileDescriptor); val out = FileOutputStream(d.fileDescriptor)
        val buf = ByteArray(32767)
        try {
            while (running) {
                val n = inp.read(buf)
                if (n < 28 || (buf[0].toInt() shr 4) != 4 || buf[9].toInt() != 17) continue
                val pkt = buf.copyOf(n)
                Thread { try { relay(pkt, out) } catch (_: Exception) {} }.start()
            }
        } catch (_: Exception) {}
        running = false
    }

    private fun relay(p: ByteArray, out: FileOutputStream) {
        val ihl = (p[0].toInt() and 15) * 4
        val q = p.copyOfRange(ihl + 8, p.size)
        var r: ByteArray? = null
        for (ip in upstream) {
            val s = DatagramSocket(); protect(s); s.soTimeout = 3000
            try {
                s.send(DatagramPacket(q, q.size, InetAddress.getByAddress(ip), 53))
                val rp = DatagramPacket(ByteArray(4096), 4096); s.receive(rp)
                r = rp.data.copyOf(rp.length); break
            } catch (_: Exception) { } finally { s.close() }
        }
        val a = r ?: return
        val len = 28 + a.size; val o = ByteArray(len)
        o[0] = 0x45; o[2] = (len shr 8).toByte(); o[3] = len.toByte(); o[8] = 64; o[9] = 17
        System.arraycopy(p, 16, o, 12, 4); System.arraycopy(p, 12, o, 16, 4)
        var sum = 0
        for (k in 0 until 20 step 2) sum += ((o[k].toInt() and 255) shl 8) or (o[k + 1].toInt() and 255)
        while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        val ck = sum.inv() and 0xFFFF; o[10] = (ck shr 8).toByte(); o[11] = ck.toByte()
        o[20] = p[ihl + 2]; o[21] = p[ihl + 3]; o[22] = p[ihl]; o[23] = p[ihl + 1]
        val ul = 8 + a.size; o[24] = (ul shr 8).toByte(); o[25] = ul.toByte()
        System.arraycopy(a, 0, o, 28, a.size)
        synchronized(out) { out.write(o) }
    }
}
