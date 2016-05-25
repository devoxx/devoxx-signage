/*
 * Devoxx digital signage project
 */
package devoxx;

import devoxx.model.Speaker;
import devoxx.model.Presentation;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import static javafx.animation.Animation.INDEFINITE;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

/**
 * The main Devoxx class.
 * 
 * @author Angie
 * @author Stephan007
 */
public class Devoxx extends Application {

    private final static Logger LOGGER = Logger.getLogger(Devoxx.class.getName());
    private final static ConsoleHandler CONSOLDE_HANDLER = new ConsoleHandler();

    private static final int FIVE_MINUTES = 5;
    private static final int HALF_HOUR = 30;
    
    private ControlProperties controlProperties;
    private FXMLDocumentController screenController;

    private DataFetcher dataFetcher;
    private String roomName;
    
    private final List<Presentation> newPresentations = new ArrayList<>();
    private List<Presentation> presentations;
    private Presentation currentPresentation = null;
    private Presentation firstPresentation;
    private Presentation secondPresentation;
    private Presentation thirdPresentation;

    /**
     * Entry point for the JavaFX application life cycle.
     *
     * @param stage Where to present the scene
     * @throws Exception If there is an error
     */
    @Override
    public void start(final Stage stage) throws Exception {
                        
        // Get property values
        final String roomId = getRoomFromJVMParam();
        final String propertiesFile = getPropertiesFileFromJVMParam();

        // Print configuration info to std out for debugging
        printConfigInfo(propertiesFile, roomId);

        // Retrieving all the Devoxx schedule data for the provided room
        fetchRoomSchedule(roomId);

        // Get room name based on Devoxx BE or UK naming conventions
        roomName = getRoomName(roomId);

        // Start the JavaFX scene 
        startFXScene(stage);

        // Start data and JavaFX screen refresh timers
        startDataRefreshTimer();
        startScreenTimer();
    }

    /**
     * Get the Devoxx properties file.
     * @return full path to property file
     */
    private String getPropertiesFileFromJVMParam() {        
        List<String> parameters = getParameters().getRaw();
        
        String propertiesFile = null;
        if (parameters.size() > 1) {
            propertiesFile = parameters.get(1);
        }
        return propertiesFile;
    }

    /**
     * Get the required room ID from JVM parameters.
     * @return the room ID
     */
    private String getRoomFromJVMParam() {
        List<String> parameters = getParameters().getRaw();
        
        final String room = parameters.isEmpty() ? null : getParameters().getRaw().get(0).toLowerCase();
        if (room == null || room.isEmpty()) {
            System.out.println("Please specify a room to display");
            System.exit(1);
        }
        return room;
    }

    /**
     * Fetch the data from the REST endpoint for selected room ID.
     * @param roomId the room ID
     */
    private void fetchRoomSchedule(final String roomId) {
        
        dataFetcher = new DataFetcher(controlProperties, roomId);
        
        // If the first read fails we don't really have any way to continue
        if (!dataFetcher.updateData()) {
            System.err.println("Error retrieving initial data from server");
            System.err.println("Bailing out!");
            System.exit(1);
        }
        
        presentations = dataFetcher.getPresentationList();
    }

    private void startFXScene(final Stage stage) throws IOException {
        final FXMLLoader myLoader = new FXMLLoader(getClass().getResource("FXMLDocument.fxml"));
        final Parent root = (Parent) myLoader.load();
        
        screenController = ((FXMLDocumentController) myLoader.getController());
                        
        if (controlProperties.isTestMode()) {
            root.setScaleX(controlProperties.getTestScale());
            root.setScaleY(controlProperties.getTestScale());
            
        } 
        
        screenController.setClock(controlProperties);        

        final Scene scene = new Scene(root);
        scene.setOnKeyPressed(e -> handleKeyPress(e));
        scene.setFill(null);
        
        stage.initStyle(StageStyle.UNDECORATED);
        stage.setScene(scene);
        stage.show();
        
        screenController.setRoom(roomName);
    }

    /** 
     * Use another Timeline to periodically update the screen display so
     * the time changes and the session information correctly reflects what's
     * happening
     */
    private void startScreenTimer() {        
        Timeline updateTimeline = new Timeline(new KeyFrame(
                Duration.seconds(controlProperties.getScreenRefreshTime()),
                (ActionEvent t) -> updateDisplay()));
        updateTimeline.setCycleCount(INDEFINITE);
        updateTimeline.getKeyFrames().get(0).getOnFinished().handle(null);
        updateTimeline.play();        
    }

    /**
     * Use a Timeline to periodically check for any updates to the published
     * data in case of last minute changes
     */
    private void startDataRefreshTimer() {
        Timeline downloadTimeline = new Timeline(new KeyFrame(
                Duration.minutes(controlProperties.getDataRefreshTime()),
                (ActionEvent t) -> updateData()));
        downloadTimeline.setCycleCount(INDEFINITE);
        downloadTimeline.play();
    }

    /**
     * Log some control property details.
     * 
     * @param propertiesFile the properties file
     * @param roomId the room ID
     * @throws SecurityException 
     */
    private void printConfigInfo(final String propertiesFile, 
                                 final String roomId) throws SecurityException {
        
        controlProperties = new ControlProperties(propertiesFile);
        LOGGER.setLevel(controlProperties.getLoggingLevel());
        LOGGER.setUseParentHandlers(false);
        CONSOLDE_HANDLER.setLevel(controlProperties.getLoggingLevel());
        LOGGER.addHandler(CONSOLDE_HANDLER);
        
        LOGGER.fine("===================================================");
        LOGGER.log(Level.FINE, "=== DEVOXX DISPLAY APP for [{0}]", roomId);
        
        if (controlProperties.isTestMode()) {
            LOGGER.finest("=== RUNNING IN TEST MODE...");
        }
        
        LOGGER.fine("===================================================");
    }

   /**
     * IMPORTANT:
     *
     * If you use this code for a venue that uses different naming for the
     * rooms you will need to change this. 
     * 
     * Devoxx BE uses rooms with a single digit number and two BOF rooms.
     * Devoxx UK uses room letters.
     */
    private String getRoomName(String room) {
        String roomNumber = "";
        
        if (controlProperties.isDevoxxBelgium()) {
            if (room.startsWith("room")) {
                roomNumber = room.substring("room".length());
            } else if (room.startsWith("bof")) {
                roomNumber = "BOF" + room.substring("bof".length());
            } else {
                LOGGER.severe("Room name not recognised (must be roomX or bofX)");
                System.exit(4);
            }
        } else if (controlProperties.isDevoxxUK()) {
            if (room.startsWith("room")) {
                switch (room.substring("room".length())) {
                    case "1"    : roomNumber = "A";
                    break;
                    
                    case "2"    : roomNumber = "B";
                    break;
                    
                    case "3"    : roomNumber = "C";
                    break;
                    
                    default     : roomNumber = "D";
                }
            } else {
                roomNumber = "Auditorium";
            }
        } else {
            LOGGER.severe("Don't know which Devoxx this is!!");
            System.exit(4);
        }
        return roomNumber;
    }

    private void updateData() {
        if (dataFetcher.updateData()) {
            screenController.setOnline();
            updateDisplay();
        } else {
            screenController.setOffline();
        }
    }

    /**
     * Update the display
     */
    private void updateDisplay() {
        LocalDateTime now;

        screenController.setClock(controlProperties);
        
        if (controlProperties.isTestMode()) {
            now = LocalDateTime.of(
                controlProperties.getStartDate()
                .plusDays(controlProperties.getTestDay()),
                controlProperties.getTestTime());
        } else {
            now = LocalDateTime.now();
        }

        LOGGER.log(Level.FINER, "Date and time of update = {0}", now);
        newPresentations.clear();

        for (Presentation presentation : presentations) {
            if (now.isBefore(presentation.toTime)) {
                newPresentations.add(presentation);
            }

            if (newPresentations.size() >= 3) {
                break;
            }
        }

        firstPresentation = newPresentations.size() >= 1 ? newPresentations.get(0) : null;
        secondPresentation = newPresentations.size() >= 2 ? newPresentations.get(1) : null;
        thirdPresentation = newPresentations.size() >= 3 ? newPresentations.get(2) : null;
        LOGGER.log(Level.FINE, "Screen update @ ({0})", now);

        if (currentPresentation != firstPresentation) {
            currentPresentation = firstPresentation;
            screenController.setScreenData(firstPresentation, secondPresentation, thirdPresentation);
            LOGGER.log(Level.FINER, "New presentation: {0}", firstPresentation);

            if (secondPresentation != null) {
                LOGGER.log(Level.FINER, "Second presentation: {0}", secondPresentation);
            }

            if (thirdPresentation != null) {
                LOGGER.log(Level.FINER, "Third presentation: {0}", thirdPresentation);
            }
        }
    }
    
    /**
     * Force refresh of the speakers image cache via key "R".
     */
    private void refreshImageCache() {
        
        final String imageCache = controlProperties.getImageCache();
        LOGGER.log(Level.FINER, "Recreating speaker cache at {0}", imageCache);
        
        final File file = new File(imageCache);
        if (file.isDirectory()) {
            
            int totalCacheFiles = file.listFiles().length;
            for (File cacheFile : file.listFiles()) {
                cacheFile.delete();
            }
            
            LOGGER.log(Level.FINER, "Deleted all {0} speaker cache photos", totalCacheFiles);
            
            for (Presentation preso : presentations) {
                for (Speaker speaker : preso.speakers) {
                    speaker.cachePhoto();
                    LOGGER.log(Level.FINER, "Created speaker cache for {0}", speaker.fullName);
                }
            }
        } else {
            LOGGER.log(Level.FINER, "Speaker cache does not exist {0}", imageCache);
        }        
    }

    /**
     * Add a simple way to exit the app. Obviously you need to plug a keyboard
     * in, but it's better than pulling out the power lead and hoping you don't
     * corrupt the file system or having to SSH in.
     *
     * @param keyEvent The details of which key was pressed
     */
    private void handleKeyPress(KeyEvent keyEvent) {
        KeyCode code = keyEvent.getCode();
        if (null != code) 
            switch (code) {
            case Q:
                System.exit(0);
            case UP:
                controlProperties.incrementTestTime(FIVE_MINUTES);
                updateDisplay();
                break;
            case DOWN:
                controlProperties.decrementTestTime(FIVE_MINUTES);
                updateDisplay();
                break;
            case LEFT:
                controlProperties.decrementTestTime(HALF_HOUR);
                updateDisplay();
                break;
            case RIGHT:
                controlProperties.incrementTestTime(HALF_HOUR);
                updateDisplay();
                break;
            case U:
                updateDisplay();
                break;
            case D:
                updateData();
                break;
            case R:
                refreshImageCache();
                updateData();
                break;
            case T:
                controlProperties.toggleRunMode();
                updateDisplay();
                updateData();
                break;
            default:
                break;
        }
    }
    
    /**
     * The main() method is ignored in correctly deployed JavaFX application.
     * main() serves only as fallback in case the application can not be
     * launched through deployment artifacts, e.g., in IDEs with limited FX
     * support. NetBeans ignores main().
     *
     * @param args the command line arguments
     */
    public static void main(String[] args) {        
        launch(args);
    }
}
