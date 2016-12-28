/*
 * Devoxx digital signage project
 */
package devoxx;

import devoxx.Devoxx;
import devoxx.model.Speaker;
import devoxx.model.Presentation;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URL;
import java.net.UnknownHostException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Enumeration;
import java.util.ResourceBundle;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;

/**
 *
 * @author Angie
 */
public class FXMLDocumentController implements Initializable {

    private static final int MAX_VISIBILE_SPEAKER_THUMBNAILS = 3;
    
    private static final String FONTS_GILL_SANSTTC = "fonts/GillSans.ttc";
    private static final String FONTS_GOTHAM_EXLIGHT_WEBFONT_TTF = "fonts/gothamexlight-webfont.ttf";
    private static final String FONTS_Q_TYPE_OT_SEEXT_MEDIUMOTF = "fonts/QTypeOT-SeextMedium.otf";
    private static final String FONTS_GOTHAMBOOK_WEBFONT_TTF = "fonts/gothambook-webfont.ttf";
    private static final String FONTS_ARIAL = "Arial";

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    
    private final BooleanProperty offline = new SimpleBooleanProperty(false);
    
    private Timeline timeline;

    @FXML
    Label sessionLbl, roomLbl, roomNumber, currentTimeTitleLbl, time,
        sessionTitle, sessionTime, sessionAbstract, sessionsTitleLbl,
        talk1Time, talk1Title, talk2Time, talk2Title, talk3Time, talk3Title,
        talk2Speaker, talk3Speaker;

    @FXML
    VBox speakersVBox, speaker1, speaker2, speaker3;
    
    @FXML
    ImageView speakerImg1, speakerImg2, speakerImg3;

    @FXML
    Label speakerName1, speakerName2, speakerName3, ipaddress, debugLabel;

    @FXML
    Rectangle debugBox;
            
    Font lightFont, qTypeBig, qTypeSml, titleThin, gothambookBig,
        gothambookMed, gothambookSml, gothambookTiny, 
        titleBig, timeFont, roomNumberFont, arialSmall;
    
    @FXML Circle networkCircle;

    /**
     * Exit the application
     *
     * @param event An associated mouse event
     */
    @FXML
    private void quitApp(MouseEvent event) {
        System.exit(0);
    }

    /**
     * Initialize various aspects of the display
     *
     * @param url The URL (not used in this app)
     * @param rb The resource bundle (not used in this app)
     */
    @Override
    public void initialize(final URL url, final ResourceBundle rb) {
        ipaddress.setText(getPublicIpAddress());
        networkCircle.visibleProperty().bind(offline);
        
        // Load fonts 
        lightFont = Font.loadFont( Devoxx.class.getResource(FONTS_GOTHAMBOOK_WEBFONT_TTF).toExternalForm(), 20);

        titleThin = Font.loadFont(Devoxx.class.getResource(FONTS_GOTHAM_EXLIGHT_WEBFONT_TTF).toExternalForm(), 40);
        qTypeBig = Font.loadFont(Devoxx.class.getResource(FONTS_Q_TYPE_OT_SEEXT_MEDIUMOTF).toExternalForm(), 30);
        qTypeSml = Font.loadFont(Devoxx.class.getResource(FONTS_Q_TYPE_OT_SEEXT_MEDIUMOTF).toExternalForm(), 23);
        gothambookBig = Font.loadFont(Devoxx.class.getResource(FONTS_GOTHAMBOOK_WEBFONT_TTF).toExternalForm(), 35);
        gothambookMed = Font.loadFont(Devoxx.class.getResource(FONTS_GOTHAMBOOK_WEBFONT_TTF).toExternalForm(), 28);
        gothambookSml = Font.loadFont(Devoxx.class.getResource(FONTS_GOTHAMBOOK_WEBFONT_TTF).toExternalForm(), 25);
        gothambookTiny = Font.loadFont(Devoxx.class.getResource(FONTS_GOTHAMBOOK_WEBFONT_TTF).toExternalForm(), 18);
        arialSmall = Font.font(FONTS_ARIAL, FontWeight.LIGHT, 18);
        setFonts();        
    }
    
    public void setClock(final ControlProperties ctrl) {
                
        if (ctrl.isTestMode()) {
            time.setTranslateX(-150);
            time.setText(ctrl.getTestTime().format(TIME_FORMAT)+ " - TEST");            
            if (timeline != null) {
                timeline.stop();
            }
        } else {
            time.setTranslateX(0);
            KeyFrame keyFrame = new KeyFrame(Duration.minutes(1),
                    t -> time.setText(LocalTime.now().format(TIME_FORMAT)));
            
            if (timeline == null) {
                timeline = new Timeline(keyFrame);
            }
            
            timeline.setCycleCount(Timeline.INDEFINITE);
            timeline.getKeyFrames().get(0).getOnFinished().handle(null);           
            timeline.play();                
        }
    }

    /**
     * Set the fonts to be used for the different parts of the display
     */
    public void setFonts() {
        sessionTime.setFont(qTypeBig);
        talk1Time.setFont(qTypeBig);
        talk2Time.setFont(qTypeSml);
        talk3Time.setFont(qTypeSml);
        currentTimeTitleLbl.setFont(titleThin);
        sessionsTitleLbl.setFont(titleThin);
        sessionAbstract.setFont(gothambookMed);
        talk1Title.setFont(gothambookBig);
        talk2Title.setFont(gothambookSml);
        talk2Speaker.setFont(arialSmall);
        talk3Title.setFont(gothambookSml);
        talk3Speaker.setFont(arialSmall);
        sessionLbl.setFont(Font.font(FONTS_ARIAL, FontWeight.BOLD, 83));
        roomLbl.setFont(Font.font(FONTS_ARIAL, FontWeight.BOLD, 83));
        roomNumber.setFont(Font.font(FONTS_ARIAL, FontWeight.BOLD, 195));
        sessionTitle.setFont(Font.font(FONTS_ARIAL, FontWeight.BOLD, 45));
        time.setFont(Font.font(FONTS_ARIAL, FontWeight.BOLD, 90));
    }
    
    public void showDebugMsg(String msg) {
        debugBox.setVisible(true);
        debugLabel.setVisible(true);        
        debugLabel.setText(msg);
    }
    
    public void hideDebug() {
        debugBox.setVisible(false);
        debugLabel.setVisible(false);                        
    }
    
    /**
     * For debugging reasons show the public IP address of the PI.
     * @return public IP
     */
    private String getPublicIpAddress() {
        String res = "NoIP Cache";
        try {
            String localhost = InetAddress.getLocalHost().getHostAddress();
            Enumeration<NetworkInterface> e = NetworkInterface.getNetworkInterfaces();
            while (e.hasMoreElements()) {
                NetworkInterface ni = (NetworkInterface) e.nextElement();
                if(ni.isLoopback()) {
                    continue;
                }
                if(ni.isPointToPoint()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while(addresses.hasMoreElements()) {
                    InetAddress address = (InetAddress) addresses.nextElement();
                    if(address instanceof Inet4Address) {
                        String ip = address.getHostAddress();
                        if(!ip.equals(localhost))
                            System.out.println("Public IP :" + (res = ip));
                    }
                }
            }
        } catch (UnknownHostException | SocketException e) {
            System.out.println(e.getCause()); 
        }
        return res;
    }

    /**
     * Set the data for the screen
     *
     * @param mainPreso The main presentation that is on now or next
     * @param secondPreso The next presentation (if there is one)
     * @param thirdPreso The presentation after next (if there is one)
     */
    public void setScreenData(Presentation mainPreso,
        Presentation secondPreso, Presentation thirdPreso) {
        /* Remove current data from the speaker VBox */
        if (speakersVBox.getChildren() != null) {
            while (speakersVBox.getChildren().size() > 0) {
                speakersVBox.getChildren().remove(0);
            }
        }

        if (mainPreso != null && mainPreso.title != null) {
            sessionTitle.setText((mainPreso.title).toUpperCase());
            sessionAbstract.setText(mainPreso.summary);            
            sessionTime.setText(mainPreso.fromTime.format(TIME_FORMAT) + " - "
                + mainPreso.toTime.format(TIME_FORMAT));

            /**
             * Sort out the speaker photos and names. This has proved to be
             * incredibly difficult, I know not why. There seems to be some
             * weirdness with some of the photos so they occupy a bigger space
             * than they should which screws things up (if we just use a simple
             * VBox). This still does not work properly (Mark Reinhold is a good
             * test case)
             */
            if (mainPreso.speakers.length == 1) {
                speakersVBox.setSpacing(0);
            } else if (mainPreso.speakers.length == 2) {
                speakersVBox.setSpacing(30);
            } if (mainPreso.speakers.length == 3) {
                speakersVBox.setSpacing(20);
            } else if (mainPreso.speakers.length > MAX_VISIBILE_SPEAKER_THUMBNAILS) {
                speakersVBox.setSpacing(40);
            } 
            
            for (Speaker speaker : mainPreso.speakers) {

                VBox speakerBox = new VBox();
                speakerBox.setSpacing(5);
                
                // Only show speaker thumbnail if they can all fit  :) 
                if (mainPreso.speakers.length <= MAX_VISIBILE_SPEAKER_THUMBNAILS) {
                    HBox photoBox = new HBox();
                    photoBox.setAlignment(Pos.CENTER);
                    photoBox.getChildren().add(speaker.getPhoto());
                    speakerBox.getChildren().add(photoBox);
                } 
                
                HBox nameBox = new HBox();
                nameBox.setAlignment(Pos.CENTER);
                Label name = new Label(speaker.fullName.toUpperCase());                
                name.setFont(arialSmall);
                nameBox.getChildren().add(name);
                speakerBox.getChildren().add(nameBox);

                speakersVBox.getChildren().add(speakerBox);
            }
            talk1Title.setText(mainPreso.title);
            talk1Time.setText(mainPreso.fromTime.format(TIME_FORMAT) + " - "
                + mainPreso.toTime.format(TIME_FORMAT));
        } else {
            sessionTitle.setText("");
            sessionAbstract.setText("");
            sessionTime.setText("");
            talk1Title.setText("");
            talk1Time.setText("");
        }

        if (secondPreso != null) {
            
            talk2Speaker.setText(secondPreso.getSpeakerList());
            talk2Title.setText(secondPreso.title);
            talk2Time.setText(secondPreso.fromTime.format(TIME_FORMAT) + " - "
                + secondPreso.toTime.format(TIME_FORMAT));
        } else {
            talk2Title.setText("");
            talk2Speaker.setText("");
            talk2Time.setText("");
        }

        if (thirdPreso != null) {
            talk3Speaker.setText(thirdPreso.getSpeakerList());
            talk3Title.setText(thirdPreso.title);
            talk3Time.setText(thirdPreso.fromTime.format(TIME_FORMAT) + " - "
                + thirdPreso.toTime.format(TIME_FORMAT));
        } else {            
            talk3Title.setText("");
            talk3Speaker.setText("");
            talk3Time.setText("");
        }
    }
    
    public void setOnline() {
        offline.set(false);
    }
    
    public void setOffline() {
        offline.set(true);
    }

    /**
     * Set which room data is being displayed for.
     *
     * @param room The name of the room
     */
    public void setRoom(String room) {
        /**
         * For Devoxx BE there are two BOF rooms, which we treat differently by
         * moving the labels for the session and room and reducing the font size
         * of the room number
         */
        
        if (room.startsWith("BOF")) {
            roomNumber.setFont(Font.font("Arial", FontWeight.BOLD, 120));
            roomNumber.setTranslateX(-160);
            roomNumber.setTranslateY(30);
            sessionLbl.setTranslateX(-150);
            roomLbl.setTranslateX(-150);
        } else if (room.equals("10")) {
            roomNumber.setTranslateX(-70);
            sessionLbl.setTranslateX(-70);
            roomLbl.setTranslateX(-70);
        } else {
            roomNumber.setFont(Font.font("Arial", FontWeight.BOLD, 195));
        }

        roomNumber.setText(room);
        
        if (room.equalsIgnoreCase("A") || 
            room.equalsIgnoreCase("B") || 
            room.equalsIgnoreCase("C") || 
            room.equalsIgnoreCase("D")) {
            sessionLbl.setText("SESSION");
            sessionLbl.setTranslateX(10);
            sessionLbl.setTranslateY(0);
            sessionLbl.setFont(Font.font("Arial", FontWeight.BOLD, 80));
            roomLbl.setText("ROOM");
            roomLbl.setTranslateX(0);
            roomNumber.setTranslateX(0);
        } else if (room.length() > 4) {
            // Shows the "Auditorium" room label for Devoxx UK
            sessionLbl.setText(room);
            sessionLbl.setTranslateX(-60);
            sessionLbl.setTranslateY(20);
            sessionLbl.setFont(Font.font("Arial", FontWeight.BOLD, 120));
            roomNumber.setText("");
            roomLbl.setText("");
        } 
    }
}
