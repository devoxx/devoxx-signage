/*
 * Devoxx digital signage project
 */
package devoxx;

import devoxx.model.Speaker;
import devoxx.model.Presentation;
import devoxx.json.PresentationDeserializer;
import devoxx.json.SpeakerDeserializer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Class for loading all session data from Devoxx server
 *
 * @author Jasper Potts
 * @author Stephan
 */
public class DataFetcher {

    private final static Logger LOGGER = Logger.getLogger(DataFetcher.class.getName());
    
    // JSON file names
    private static final String SPEAKERS_JSON = "speakers.json";

    private static final String[] DAYS = {"monday", "tuesday", "wednesday", "thursday", "friday"};

    private final Map<String, Speaker> speakerMap = new HashMap<>();
    private final Map<String, Presentation> presentationMap = new HashMap<>();
    private final List<Presentation> presentations = new ArrayList<>();
    
    private String roomId;
    private final String devoxxHost;
    private final LocalDate startDate;
    private final String imageCache;

    /**
     * Constructor
     *
     * @param controlProperties control properties
     * @param roomId Which room to get data for
     */
    public DataFetcher(final ControlProperties controlProperties,
                       final String roomId) {
        this.roomId = roomId;
        devoxxHost = controlProperties.getDevoxxHost();
        imageCache = controlProperties.getImageCache();
        startDate = controlProperties.getStartDate();
    }
    
    public void setRoomId(final String roomId) {
        this.roomId = roomId;
    }
    
    public void clearAll() {
        speakerMap.clear();
        presentationMap.clear();
        presentations.clear();
    }

    /**
     * Get the list of presentations for the chosen room
     *
     * @return
     */
    public List<Presentation> getPresentationList() {
        return presentations;
    }

    /**
     * Try to update the data from the Devoxx CFP web service.
     *
     * @return Whether the update succeeded or failed
     */
    public boolean updateData() {
        LOGGER.log(Level.FINE, "Retrieving data for room {0}", roomId);

        if (!retrieveSpeakerDetails()) {
            return false;
        }

        if (!retrieveScheduleDetails()) {
            return false;
        }

        sortPresentations();

        return true;
    }

    /** 
     * Sort the presentation by time.  I'm not sure this is really
     * necessary given the size of the data set (SR)
     */
    private void sortPresentations() {
        presentations.clear();
        presentations.addAll(presentationMap.values());
        Collections.sort(presentations, (s1, s2) -> s1.fromTime.compareTo(s2.fromTime));
    }

    /**
     * Retrieve all the session data for the week.
     *
     * @return true when successful
     */
    private boolean retrieveScheduleDetails() {

        final GsonBuilder presoJSONBuilder = new GsonBuilder();        
        presoJSONBuilder.registerTypeAdapter(Presentation.class, new PresentationDeserializer(speakerMap, imageCache));
        final Gson gson = presoJSONBuilder.create();

        for (String day : DAYS) {
            try {
                LOGGER.log(Level.FINER, "Retrieving data for {0}", day);
                String dataUrl = devoxxHost + "rooms/" + roomId + "/" + day;
                
                LOGGER.log(Level.FINEST, "{0} URL = {1}", new Object[]{day, dataUrl});
                String jsonString = "schedule-" + day + ".json";
                
                ResourceUtil.download(dataUrl, jsonString);
                parseScheduleJsonFile(jsonString, gson);
            
            } catch (IOException | JsonParseException e) {
                LOGGER.log(Level.SEVERE, "Failed to retrieve schedule for {0}", day);
                LOGGER.severe(e.getMessage());
            }
        }

        if (presentationMap.isEmpty()) {
            LOGGER.severe("Error: No presentation data downloaded!");
            return false;
        }

        LOGGER.log(Level.INFO, "Found [{0}] PRESENTATIONS\n", presentationMap.size());
        return true;
    }

    private void parseScheduleJsonFile(String jsonString, Gson gson) throws IOException, JsonParseException {
                
        final FileInputStream in = new FileInputStream(new File(jsonString));
        
        try(Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            JsonParser parser = new JsonParser();
            JsonElement root = parser.parse(reader);
            
            JsonObject obj = root.getAsJsonObject();
            
            JsonArray slots = obj.getAsJsonArray("slots");
            
            if (slots.size() > 0) {
                Presentation presentations[] = gson.fromJson(slots, Presentation[].class);
                
                for (Presentation presentation : presentations) {
                    if (presentation != null && !presentation.title.isEmpty()) {
                        presentationMap.put(presentation.id, presentation);
                    }
                }
            }
        }
    }

    /**
     * Retrieve all the speakers data.
     *
     * @return true when successful
     */
    private boolean retrieveSpeakerDetails() {
        LOGGER.finer("Retrieving speaker data...");

        try {
            ResourceUtil.download(devoxxHost + "speakers", SPEAKERS_JSON);

            parseSpeakersJSONFile();

        } catch (IOException | JsonParseException e) {
            LOGGER.severe("Failed to retrieve speaker data!");
            LOGGER.severe(e.getMessage());
            return false;
        }
        
        LOGGER.log(Level.INFO, "Found [{0}] SPEAKERS", speakerMap.size());
        return true;
    }

    private void parseSpeakersJSONFile() throws IOException, JsonParseException {
        
        // Create the JSON Builder
        final GsonBuilder speakerJSONBuilder = new GsonBuilder();
        speakerJSONBuilder.registerTypeAdapter(Speaker.class, new SpeakerDeserializer(imageCache));
        final Gson gson = speakerJSONBuilder.create();
        
        Speaker[] speakers;
        
        // Read Speakers JSON file and deserialize
        FileInputStream in = new FileInputStream(new File(SPEAKERS_JSON));
        try(Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)){
            speakers = gson.fromJson(reader, Speaker[].class);
        }
        
        // Populate the speaker map
        for (Speaker speaker : speakers) {
            speakerMap.put(speaker.uuid, speaker);
        }        
    }
}