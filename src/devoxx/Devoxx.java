/*
 * Devoxx digital signage project
 */
package devoxx;

import devoxx.model.Speaker;
import devoxx.model.Presentation;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
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
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.concurrent.Task;
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

    private static final String CURRENT_ROOMTXT = "currentRoom.txt";
    
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
    
    private BooleanProperty updating = new SimpleBooleanProperty();

    /**
     * Entry point for the JavaFX application life cycle.
     *
     * @param stage Where to present the scene
     * @throws Exception If there is an error
     */
    @Override
    public void start(final Stage stage) throws Exception {

        // Get property values
        final String roomId = getRoomFromSystem();
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
        updating.addListener(e -> {
            System.out.println("Updating changed, is now "+updating.get());
            if (!updating.get()) {
                System.out.println("hide debug!");
                screenController.hideDebug();
            }
        });
    }

    /**
     * Get the Devoxx properties file.
     *
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
     *
     * @return the room ID
     */
    private String getRoomFromSystem() {
        String room = null;
                
        final File file = new File(CURRENT_ROOMTXT);
        if (!file.exists()) {
            
            // Current room text file doesn't exist, lets get it from property params
            List<String> parameters = getParameters().getRaw();

            room = parameters.isEmpty() ? null : getParameters().getRaw().get(0).toLowerCase();
            if (room == null || room.isEmpty()) {
                System.out.println("Please specify a room to display");
                System.exit(1);
            }
        } else {            
            // Lets get the room id from the local current room text file            
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                room = br.readLine();
            } catch(IOException e) {
                System.out.println("Problems reading currentRoom.txt file");
                System.exit(1);
            }
        }
        
        return room;
    }    
    
    private void writeRoomToSystem(String room) {
        try(PrintWriter out = new PrintWriter(CURRENT_ROOMTXT)) {
            out.println( room );
        } catch(IOException e) {
            System.out.println(e);
            System.out.println("Problems writing to currentRoom.txt file");
            System.exit(1);
        }
    }

    /**
     * Fetch the data from the REST endpoint for selected room ID.
     *
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
        
        screenController.hideDebug();
    }

    /**
     * Use another Timeline to periodically update the screen display so the
     * time changes and the session information correctly reflects what's
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
     * Use a Time line to periodically check for any updates to the published
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
     * If you use this code for a venue that uses different naming for the rooms
     * you will need to change this.
     *
     * Devoxx BE uses rooms with a single digit number and two BOF rooms. Devoxx
     * UK uses room letters.
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
                    case "1":
                        roomNumber = "A";
                        break;

                    case "2":
                        roomNumber = "B";
                        break;

                    case "3":
                        roomNumber = "C";
                        break;

                    default:
                        roomNumber = "D";
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

    /**
     * This needs to be called on the FX App thread.
     * When we update the data, we set the updating property to true.
     * We create a task on a new thread, and start it.
     * When the task succeeds, we set the updating property to false.
     * As a consequence, toggling the update property happens on the FX App thread, 
     * while fetching the data happens on another thread.
     * Note that we need to update the display on the FX App thread, hence
     * th call to Platform.runLater()
     */
    private void updateData() {
        if (!Platform.isFxApplicationThread()) {
            System.err.println("This should never happen: updateData is called from a non-FX Thread. See stack trace below:");
            Thread.dumpStack();
            return;
        }
        updating.set(true);
        Task task = new Task() {
            @Override
            protected Object call() throws Exception {
                try {

                    if (dataFetcher.updateData()) {
                        screenController.setOnline();
                        Platform.runLater(() -> updateDisplay());
                    } else {
                        screenController.setOffline();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return null;
            }
        };
        Thread t = new Thread(task);
        task.setOnSucceeded(e -> updating.set(false));
        t.start();
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
    
    private void setRoom(int roomNumber) {

        // TODO How to force a screen refresh before we continue, different thread?
        screenController.showDebugMsg("Fetching data room " + roomNumber);        
              System.out.println("SHOW debug!");
        String roomId;
        if (roomNumber == 0) {
            roomId = "aud_room";
        } else {
            roomId = "room" + roomNumber; 
        }

        writeRoomToSystem(roomId);

        screenController.setRoom(getRoomName(roomId));

        dataFetcher.clearAll();

        dataFetcher.setRoomId(roomId);

        currentPresentation = null;

        updateData();

       // screenController.hideDebug();
       
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
        if (null != code) {
            switch (code) {
                case DIGIT0:
                    setRoom(0);
                    break;
                case DIGIT1:
                    setRoom(1);
                    break;
                case DIGIT2:
                    setRoom(2);
                    break;
                case DIGIT3:
                    setRoom(3);
                    break;
                case DIGIT4:
                    setRoom(4);
                    break;
                case DIGIT5:
                    setRoom(5);
                    break;
                case DIGIT6:
                    setRoom(6);
                    break;
                case DIGIT7:
                    setRoom(7);
                    break;
                case DIGIT8:
                    setRoom(8);
                    break;
                case DIGIT9:
                    setRoom(9);
                    break;
                case Q:
                    screenController.showDebugMsg("Quitting");
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
                    screenController.showDebugMsg("Update display");
                    updateDisplay();
                    break;
                case D:
                    screenController.showDebugMsg("Update data");
                    updateData();
                    break;
                case R:
                    screenController.showDebugMsg("Reloading data");
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
        
     //   screenController.hideDebug();
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
