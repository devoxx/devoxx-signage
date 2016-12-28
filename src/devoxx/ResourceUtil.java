package devoxx;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Load a file from the given URL.
 */
public class ResourceUtil {


    /**
     * Download the given JSON file
     *
     * @param url The URL to load and parse
     * @param fileName
     * @return true in case we could download and parse the URL, false in case there is no Internet connection
     * @throws IOException if thrown by the stream
     */
    public static boolean download(String url, String fileName)
        throws IOException {
        InputStream in;
        System.out.println("I have to download "+url);
        try {
            final URL urlObj = new URL(url);
            HttpURLConnection connection = (HttpURLConnection) urlObj.openConnection();
            in = connection.getInputStream();
            File tempFile = new File(fileName + ".tmp");
            BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            String line = br.readLine();

            try (BufferedWriter os = new BufferedWriter(new FileWriter(tempFile))) {
                while (line != null) {
                    os.write(line);
                    os.write('\n');
                    line = br.readLine();
                }
            }
            in.close();
            File destFile = new File(fileName);
            destFile.delete();
            tempFile.renameTo(destFile);
            return true;
        } catch (IOException ex) {
            System.out.println("INTERNET IS DOWN, USING CACHED DATA.  :)");
            ex.printStackTrace();
        }
        return false;
    }
}
