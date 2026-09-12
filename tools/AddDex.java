import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** 把 classes.dex 以 STORED（不压缩）方式写入 APK，并与 AGP 行为一致 */
public class AddDex {
    public static void main(String[] args) throws Exception {
        String inApk = args[0];
        String outApk = args[1];
        String dexPath = args[2];
        ZipFile src = new ZipFile(inApk);
        ZipOutputStream out = new ZipOutputStream(new FileOutputStream(outApk));
        Enumeration<? extends ZipEntry> en = src.entries();
        while (en.hasMoreElements()) {
            ZipEntry e = en.nextElement();
            if (e.getName().equals("classes.dex")) continue;
            ZipEntry ne = new ZipEntry(e.getName());
            ne.setMethod(e.getMethod());
            if (e.getMethod() == ZipEntry.STORED) {
                ne.setSize(e.getSize());
                ne.setCompressedSize(e.getSize());
                ne.setCrc(e.getCrc());
            }
            ne.setTime(e.getTime());
            out.putNextEntry(ne);
            InputStream is = src.getInputStream(e);
            copy(is, out);
            is.close();
            out.closeEntry();
        }
        File dex = new File(dexPath);
        ZipEntry de = new ZipEntry("classes.dex");
        de.setMethod(ZipEntry.STORED);
        de.setSize(dex.length());
        de.setCompressedSize(dex.length());
        de.setCrc(crc(dex));
        out.putNextEntry(de);
        InputStream is = new FileInputStream(dex);
        copy(is, out);
        is.close();
        out.closeEntry();
        out.close();
        src.close();
        System.out.println("classes.dex added (stored): " + dex.length() + " bytes");
    }

    static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) > 0) out.write(b, 0, n);
    }

    static long crc(File f) throws IOException {
        CRC32 c = new CRC32();
        FileInputStream in = new FileInputStream(f);
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) > 0) c.update(b, 0, n);
        in.close();
        return c.getValue();
    }
}
