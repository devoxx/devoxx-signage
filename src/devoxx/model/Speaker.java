/*
 * Devoxx digital signage project
 */
package devoxx.model;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.shape.Circle;

/**
 * Model object for conference Speaker.
 *
 * @author Jasper Potts
 */
public class Speaker {

    private final static Logger LOGGER = Logger.getLogger(Speaker.class.getName());

    public String uuid;
    public String fullName;
    public String downloadURL;
    public Image photoImage;
    private String cache;

    public Speaker() {
    }

    
    /**
     * Constructor
     *
     * @param uuid Unique user ID
     * @param fullName The speaker's full name
     * @param downloadURL The URL for where the image comes from
     * @param cache Directory to use for image cache
     */
    public Speaker(final String uuid,
            final String fullName,
            final String downloadURL,
            final String cache) {
        this.uuid = uuid;
        this.fullName = fullName;
        this.downloadURL = downloadURL;
        this.cache = cache;
    }

    /**
     * Cache the photo for a speaker if necessary
     */
    public void cachePhoto() {
        String photoFileName = cache + File.separatorChar + uuid + ".dat";

        /* Nothing to see here, move along */
        if (Files.exists(Paths.get(photoFileName), LinkOption.NOFOLLOW_LINKS)) {
            return;
        }

        LOGGER.log(Level.FINE, "Caching photo for {0}", fullName);
        File cacheFile = new File(photoFileName);

        if (downloadURL != null && downloadURL.isEmpty()) {
            return;
        }

        if (downloadURL.contains("\\")) {
            LOGGER.log(Level.WARNING, "Image URL badly formed: {0}", downloadURL);
            LOGGER.warning("Trying to fix this");
            downloadURL = downloadURL.replace("\\", "/");
        }

        try {
            final URL imageURL = new URL(downloadURL);

            if (!Files.exists(Paths.get(cache), LinkOption.NOFOLLOW_LINKS)) {
                Files.createDirectory(Paths.get(cache));
            }

            final URLConnection connection = imageURL.openConnection();
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.11 (KHTML, like Gecko) Chrome/23.0.1271.95 Safari/537.11");

            try (DataInputStream photoInputStream = new DataInputStream(connection.getInputStream());
                 DataOutputStream cacheFileOutputStream = new DataOutputStream(new FileOutputStream(cacheFile))) {
                byte[] buf = new byte[10240];
                int byteCount;

                while ((byteCount = photoInputStream.read(buf)) > 0) {
                    cacheFileOutputStream.write(buf, 0, byteCount);
                }
            }
        } catch (Exception ioe) {
            LOGGER.log(Level.WARNING, "Unable to read photo for " + fullName + " from " + downloadURL, ioe);
        }
    }

    /**
     * Get the photo for a speaker
     *
     * @return
     */
    public ImageView getPhoto() {
        ImageView photo;

        String photoFileName = cache + File.separatorChar + uuid + ".dat";

        LOGGER.log(Level.FINER, "New speaker: {0}", fullName);

        /* Load the image from the cache if it's available, otherwise go out 
     * to the URL, load it and cache it.
         */
        if (Files.exists(Paths.get(photoFileName), LinkOption.NOFOLLOW_LINKS)) {
            LOGGER.log(Level.FINE, "Photo for {0} found in cache", fullName);
            LOGGER.log(Level.FINER, "Cache file: {0}", photoFileName);

            try {
                photoImage = new Image(new FileInputStream(photoFileName));
            } catch (FileNotFoundException ex) {
                LOGGER.severe("FileNotFound error, which cannot happen!");
            }
        } else {
            LOGGER.log(Level.FINE, "Downloading photo for {0}", fullName);
            cachePhoto();
            photoImage = new Image(downloadURL, 150, 150, true, true);
        }

        /* Create an ImageView for the Image */
        photo = new ImageView(photoImage);

        if (photoImage.getWidth() < photoImage.getHeight()) {
            photo.setFitWidth(150);
        } else {
            photo.setFitHeight(150);
        }

        photo.setPreserveRatio(true);
        final double squareDim = Math.min(photoImage.getWidth(), photoImage.getHeight());
        photo.setViewport(new Rectangle2D((photoImage.getWidth() - squareDim) / 2,
                (photoImage.getHeight() - squareDim) / 2,
                squareDim, squareDim));
        photo.setClip(new Circle(75, 75, 75));
        LOGGER.finest("Speaker photo loaded");
        return photo;
    }

    /**
     * Simplified toString method to just return the full name
     *
     * @return The speaker's full name
     */
    @Override
    public String toString() {
        return fullName;
    }
}
