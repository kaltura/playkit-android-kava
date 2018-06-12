# Kaltura Advanced Video Analytics (KAVA) Guide

## Table of Contents

* [About Kaltura Advanced Video Analytics (KAVA)](#about-kava)
* [KAVA Integration](#kava-integration)
* [Plugin Configurations](#plugin-configurations)
* [Plugin Configuration Fields](#plugin-configuration-fields)
* [List of KAVA Events](#list-of-kava-events)
* [KAVA Event Explanations](#kava-events-explanation)
* [KAVA Parameters](#kava-parameters)
* [COMMON_PARAMS](#common_params)
* [KAVA End Session Parameters to Reset](#kava-end-session-params-to-reset)
* [Server Response JSON Structure](#server-response-json-structure)
* [Important Event Report Conditions](#eventReportUniqueConditions)

## About Kaltura Advanced Video Analytics (KAVA)

The Kaltura Advanced Video Analytics (KAVA) plugin is desinged to track and collect various events and data about the Playkit video player. Integration is quite simple using the steps detalied below.

## KAVA Integration  

KAVA is build on top of the Kaltura Playkit SDK; therefore, to get started, you'll need to add dependencies in your application build.gradle file. Note that the Playkit is already included in the KAVAPlugin.

```
dependencies {

    implementation 'com.kaltura:playkit-android-kava:XXX' //instead of XXX use latest version. 
   
}

repositories {
    maven { url 'https://jitpack.io' }
}
```

Next, lets see how to use the KAVAPlugin in your application.

```java
public class MainActivity extends AppCompatActivity {
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        //First register your plugin.
        PlayKitManager.registerPlugins(this, KavaAnalyticsPlugin.factory);
        
        //Initialize PKPluginConfigs object.
        PKPluginConfigs pluginConfigs = new PKPluginConfigs();
        
        //Initialize Json object that will hold all the configurations for the plugin.
        JsonObject pluginEntry = new JsonObject();
        
        //Put the partner id.(Mandatory field)
        pluginEntry.addProperty("partnerId", YOUR_PARTNER_ID);
        
        //Set plugin entry to the plugin configs.
        pluginConfigs.setPluginConfig(KavaAnalyticsPlugin.factory.getName(), pluginEntry);

        //Create instance of the player with specified pluginConfigs.
        player = PlayKitManager.loadPlayer(this, pluginConfigs);
    }    
}
```

## Plugin Configurations  

Like Kaltura's other Playkit plugins, KAVA includes configurations that can be used by your application.  

In the following code snippet, you can see how to configure KAVA with custom parameters. Below are detailed explanation of each field and its default values.

>Note: You can use the KavaAnalyticsConfig object or build the JSON with your configurations. 

```java
public class MainActivity extends AppCompatActivity {
    
    private Player player;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        //Create PKPluginConfigs and populate it with Kava plugin configurations.
        PKPluginConfigs pluginConfigs = createPluginConfigurations();
        
        //Create instance of the player with specified pluginConfigs.
        player = PlayKitManager.loadPlayer(this, pluginConfigs);
    }
    
    private PKPluginConfigs createPluginConfigurations() {
    
            //First register your plugin.
            PlayKitManager.registerPlugins(this, KavaAnalyticsPlugin.factory);
    
            //Initialize PKPluginConfigs object.
            PKPluginConfigs pluginConfigs = new PKPluginConfigs();
    
            //Set your configurations.
            KavaAnalyticsConfig kavaConfig = new KavaAnalyticsConfig()
                    .setPartnerId(123456) //Your partnerId. Mandatory field!
                    .setBaseUrl("yourBaseUrl")
                    .setUiConfId(123456)
                    .setKs("your_ks")
                    .setPlaybackContext("yourPlaybackContext")
                    .setReferrer("your_referrer")
                    .setDvrThreshold(1000) //Threshold from the live edge.
                    .setCustomVar1("customVar1")
                    .setCustomVar2("customVar2")
                    .setCustomVar3("customVar3");
    
            //Set Kava configurations to the PKPluginConfig.
            pluginConfigs.setPluginConfig(KavaAnalyticsPlugin.factory.getName(), kavaConfig);
    
            
            return pluginConfigs;
        }
}

```

## Plugin Configuration Fields

* [partnerId](#partnerId) - this is your Kaltura partnerId
    * Note that this is a mandatory field; without it, the KavaAnalyticsPlugin will work.
    
* baseUrl - base Url where KavaAnalytics events will be sent.
    * Default value - http://analytics.kaltura.com/api_v3/index.php
    * Optional field
    
* [uiconfId](#uiconfId) - ID of the Kaltura UI configurations.
    * Optional field

* [ks](#ks) - your Kaltura KS.
    * Optional field

* [playbackContext](#playbackContext) - you can provide your own custom context for the media playback.
This is used to send the ID of the category from which the user is playing the entry.
    * Optional field
        
* [referrer](#referrer) - your referrer.
    * Default value - "app://" + your application package name.
    * Optional field
    
* dvrThreshold - threshold from the live edge.

When the player's playback position from the live edge <= then dvrThreshold, KAVA will set [playbackType](#playbackType) to dvr. Otherwise it will be live.
    * Use milliseconds for this field
    * Default value - 120000 (2 minutes)
    * Optional field
    
* [customVar1](#customVar1), [customVar2](#customVar2), [customVar3](#customVar3) - you can use this fields for your own custom needs. 

    * Optional field
    
## List of KAVA Events

Here is a list of all available KAVA Events:

* [VIEW](#viewEvent)
* [IMPRESSION](#impressionEvent)
* [PLAY_REQUEST](#playRequestEvent)
* [PLAY](#playEvent)
* [RESUME](#resumeEvent)
* [PAUSE](#pauseEvent)
* [REPLAY](#replayEvent)
* [SEEK](#seekEvent)
* [PLAY_REACHED_25_PERCENT](#play25Event)
* [PLAY_REACHED_50_PERCENT](#play50Event)
* [PLAY_REACHED_75_PERCENT](#play75Event)
* [PLAY_REACHED_100_PERCENT](#play100Event)
* [SOURCE_SELECTED](#sourceSelectedEvent)
* [FLAVOR_SWITCH](#flavourSwitchEvent)
* [AUDIO_SELECTED](#audioSelectedEvent)
* [CAPTIONS](#captionsEvent)
* [ERROR](#errorEvent)

        
## KAVA Event Explanations  

This section provides explanations about each event, when the event is sent and which parameters are sent.

* <a id="viewEvent"></a>VIEW - Collective event that shows report for every 10 seconds of active playback.
    - eventId = 99
    - Sent every 10 second of active playback (when the player is paused, the view timer should be paused/stopped).
    - 30 seconds without a VIEW event will reset the KAVA session, so all the VIEW [specific parameters](#endSessionResetParams) should be reset as well.
    - The server may notify KAVA (via response field ["viewEventsEnabled" = false](#serverResponse)) to shut down VIEW events.
When this happens, VIEW events should be blocked from sending until the server decides to enable VIEW events again. 
    - Parameters to send:
        - [COMMON_PARAMS](#common_params)
        - [playTimeSum](#playTimeSum)
        - [bufferTime](#bufferTime)
        - [bufferTimeSum]($bufferTimeSum)
        - [actualBitrate](#actualBitrate)
        - [averageBitrate](#averageBitrate)
        
    ---
    
* <a id="impressionEvent"></a>IMPRESSION - Sent when MediaEntry is loaded(Player event LOADED_METADATA). It will be triggered only once per entry. 
    - eventId = 1    
    - Parameters to send:
        - [COMMON_PARAMS](#common_params)
    ---
    
* <a id="playRequestEvent"></a>PLAY_REQUEST - Sent when play was requested by the application(Player event PLAY received).
    - eventId = 2
    - Parameters to send:
        - [COMMON_PARAMS](#common_params)
    ---
    
* <a id="playEvent"></a>PLAY - Sent when actual playback has been started for the first time (Player PLAYING event received).
    - eventId = 3
    - Parameters to send: 
        - [COMMON_PARAMS](#common_params)
        - [bufferTime](#bufferTime)
        - [bufferTimeSum](#bufferTimeSum)
        - [actualBitrate](#actualBitrate)
        - [joinTime](#joinTime)
    ---
    
* <a id="resumeEvent"></a>RESUME - Sent when actual playback has been resumed (!NOT for the first time. Player PLAYING event received).
    - eventId = 4
    - Parameters to send:
        - [COMMON_PARAMS](#common_params)
        - [bufferTime](#bufferTime)
        - [bufferTimeSum](#bufferTimeSum)
        - [actualBitrate](#actualBitrate)
    ---
    
* <a id="pauseEvent"></a>PAUSE - Sent when playback was paused (Player PAUSE event received).
    - eventId = 33
    - During pause, KAVA should prevent from counting the VIEW event timer
    - This event should reset[sessionStartTime](#sessionStartTime) in way that next event coming after pause will hold newly received [sessionStartTime](#sessionStartTime) value.
    - Parameters to send:
        - [COMMON_PARAMS](#common_params)
    ---
    
* <a id="replayEvent"></a>REPLAY - Sent when replay is called by the application (Player REPLAY event received).
    - eventId = 34
    - Replay should reset all the parameters related to playback except PLAYER_REACHED... events
    - Parameters to send:
        - [COMMON_PARAMS](#common_params)
    ---
    
* <a id="seekEvent"></a>SEEK - Sent when seek requested (Player SEEKING event received).
    - eventId = 35
    - Parameters to send:
        - [COMMON_PARAMS](#common_params)
        - [targetPosition](#targetPosition)
    
    ---
    
* <a id="play25Event"></a>PLAY_REACHED_25_PERCENT - Sent when player reaches 25% of the playbac regardless of whether reached by seeking or through regular playback.
    - eventId = 11
    - Sent only once per entry
    - Parameters to send:
        - [COMMON_PARAMS](#common_params)
    ---
    
* <a id="play50Event"></a>PLAY_REACHED_50_PERCENT - Sent when a player reaches 50% of the playback, regardless of whether reached by seeking or through regular playback.
    - eventId = 12
    - Sent only once per entry
    - If reached before 25% (by seeking or startFrom) first will fire the following:
        - PLAY_REACHED_25_PERCENT event.
    - Parameters to send:
        - [COMMON_PARAMS](#common_params)
    
    ---

* <a id="play75Event"></a>PLAY_REACHED_75_PERCENT - Sent when a player reaches 75% of the playback, regardless of whether reached by seeking or through regular playback. 
    - eventId = 13
    - Sent only once per entry
    - If reached before 50% (by seeking or startFrom) first will fire:
        - PLAY_REACHED_25_PERCENT
        - PLAY_REACHED_50_PERCENT
    - Parameters to send:
        - [COMMON_PARAMS](#common_params)
    ---
    
* <a id="play100Event"></a>PLAY_REACHED_100_PERCENT - Sent when a player reaches 100% of the playback (Player END event), regardless of whether maximum is reached by seeking or through regular playback.
    - eventId = 14
    - Sent only once per entry.
    - If reached before 75% (by seeking or startFrom) first will fire: 
        - PLAY_REACHED_25_PERCENT
        - PLAY_REACHED_50_PERCENT
        - PLAY_REACHED_75_PERCENT
    - Parameters to send:
        - [COMMON_PARAMS](#common_params)
    ---
    
* <a id="sourceSelectedEvent"></a>SOURCE_SELECTED - Sent when a video track is changed manually (not an ABR selection; player VIDEO_TRACK_CHANGED event received).
    - eventId = 39
    - Parameters to send:
        - [COMMON_PARAMS](#common_params)
        - [actualBitrate](#actualBitrate)
    ---
    
* <a id="flavourSwitchEvent"></a>FLAVOR_SWITCH - Sent when a video flavor is changed by the ABR mode (Player PLAYBACK_INFO_UPDATED event received).
    - eventId = 43
    - Newly received bitrate != [actualBitrate](#actualBitrate)
    - Parameters to send:
        - [COMMON_PARAMS](#common_params)
        - [actualBitrate](#actualBitrate)
    ---
    
* <a id="audioSelectedEvent"></a>AUDIO_SELECTED - Sent when an audio track changed (Player AUDIO_TRACK_CHANGED event received).
    - eventId = 42
    - Parameters to send:
        - [COMMON_PARAMS](#common_params)
        - [language](#language)
    ---
    
* <a id="captionsEvent"></a>CAPTIONS - Sent when a text track changed (Player TEXT_TRACK_CHANGED event received).
    - eventId = 38
    - Parameters to send:
        - [COMMON_PARAMS](#common_params)
        - [caption](#caption)
    ---
* <a id="errorEvent"></a>ERROR - Sent when an error occurs (Player ERROR event received).
    - eventId = 98
    - Parameters to send:
        - [COMMON_PARAMS](#common_params)
        - [errorCode](#errorCode)


## KAVA Parameters  

KAVA parameters are sent with each KAVA event and represent relevant information about current playback, media information, etc.


* <a id="eventType"></a>eventType - ID of the KavaEvent.
    - In Android obtained from KavaEvent enum. For example IMPRESSION(1), VIEW(99) etc.
    >Important: This is a **mandatory field**; if it does not exist, all events should be blocked and a related warning should be printed to the log.
    ---
    
* <a id="partnerId"></a>partnerId - The partner account ID on Kaltura's platform.
    - Obtained from the pluginConfig object
    >Important: This is a **mandatory field**; if it does not exist, all events should be blocked and a related warning should be printed to the log.		
    ---

* <a id="entryId"></a>entryId - The delivered content ID on Kaltura's platform.
    - Obtained from the mediaConfig object
    >Important: This is a **mandatory field**; if it does not exist, all events should be blocked and a related warning should be printed to the log.
    
    ---
    
* <a id="flavourId"></a>flavourId - The ID of the flavor that is currently displayed (for future use, will not be used in aggregations) >Important: **!!!NOT_SUPPORTED on mobile!!!**

    ---
    
* <a id="ks"></a>ks - The Kaltura encoded session data.
    - Obtained from the pluginConfig object
    - If does not exist do not send this parameter at all
    
    ---
    
* <a id="sessionId"></a>sessionId - A unique string that identifies a unique viewing session; a page refresh should use a different identifier.
    - Obtained from the player (player.getSessionId())
    
    ---

* <a id="eventIndex"></a>eventIndex - A sequence number that describe the order of events in a viewing session. In general this is just a counter of sent events.
    - Starts from 1
    - Each event that is sent must have a unique ID and increment after each event sent
    - When a KAVA session expires/resets, this should be reset to the initial value (1). For example: 
      - A new media entry will reset this counter
      - A VIEW event that was not sent for 30 seconds also resets this value
   
    ---
  
* <a id="bufferTime"></a>bufferTime - The amount of time spent on buffering from the last VIEW event.
    - Should be 0 to 10 in VIEW events
    - Can be 0 to ∞ for PLAY/RESUME events
    - Should be in float format (second.milliSecond)
    - Resets to 0 after every VIEW event
    - A new media entry will reset this value
    - A VIEW event that was not sent for 30 seconds also resets this value

    ---
  
* <a id="bufferTimeSum"></a>bufferTimeSum - Sum of all the buffer time during the entire playback.
    - Can be 0 to ∞
    - Should be in float format (second.milliSecond)
    - A new media entry will reset this value
    - A VIEW event that was not sent for 30 seconds also resets this value
    
    ---

* <a id="actualBitrate"></a>actualBitrate - The bitrate of the displayed video track in kbps.
    - When ABR mode selected manually - value should be 0
    - In SOURCE_SELECTED the event should be equal to the video bitrate that was selected manually
    - In FLAVOUR_SWITCH the event should be based on the ABR selection and equal to the current bitrate by ABR

    ---

* <a id="referrer"></a>referrer - Application referrer ID.
    - Obtained from the pluginConfig
    - Valid referrer should start from one of the following prefixes:
      - "app://"
      - "http://"
      - "https://"
    - If the pluginConfig has no referrer or the referrer is in an invalid format, the default one should be build using the following format:
      - application id with prefix of (app://). For example: app://com.kaltura.player
    - Should be converted to Base64 (before sending to the server)
    
    ---

* <a id="deliveryType"></a>deliveryType - The player's streamer type.
    - Obtained from the pkMediaSource.getMediaFormat() when the Player SOURCE_SELECTED event is received
    - Should be re-obtained for every new media entry
    - Should be one of the following:
      - hls
      - dash
      - url (if format not mentioned explicitly or differs from previous two)
    ---
  
* <a id="playbackType"></a>playbackType - The type of the current playback.
    - Initially obtained from mediaConfig.getMediaEntry().getMediaType(). If for some reason value could not be obtained, we will take it from the player. 
    - Must be one of the following:
      - VOD - for regular media
      - Live - for live stream
      - DVR - when playback type is 'live', and the offset from the Live edge is greater then the threshold that specified in the KavaAnalyticsConfig object (the default is 2 minutes, but can be customized)
 
    ---

* <a id="sessionStartTime"></a>sessionStartTime - The timestamp of the first event in the session.
    - Obtained from the response of the first event. This is the ["time"](#server-response-json-structure) field on the JSON object that comes with the response.
    - The first event fired will not have this value
    - Should be in a Unix time format
    
    ---

* <a id="uiConfId"></a>uiConfId - The player UI configuration ID.
    - Obtained from the pluginConfig object
    - If does not exist do not send this parameter at all

    ---
  
* <a id="clientVer"></a>clientVer - The player version (PlayKitManager.CLIENT_TAG)

    ---
  
* <a id="position"></a>position - The playback position of the media.
    - Should be in a float format (second.milliSecond)
    - When [playbackType](#playbackType) is = Live; this value should represent the offset of the playback position from the live edge.
        - 0 when the media position is on the live edge
        - -2.5 when offset from the live edge is 2.5 seconds
    - Should be a positive value for VOD [playbackType](#playbackType)

    ---
  
* <a id="playbackContext"></a>playbackContext - The category ID describing the current played context.
    - Optional parameter
    - Obtained from the pluginConfig
    - If does not exist do not send this parameter at all

    ---

* <a id="customVar1"></a>customVar1, <a id="customVar2"></a>customVar2, <a id="customVar3"></a>customVar3 - Optional parameter defined by the user.
    - Can be any primitive value or string
    - Optional parameter
    - Obtained from the pluginConfig
    - If does not exist do not send this parameter at all
  
    ---
  
* <a id="targetPosition"></a>targetPosition - The requested seek position of the media. 
    - Should be in a float format (second.milliSecond)
    - Obtained from the player SEEKING event

    ---
  
* <a id="errorCode"></a>errorCode - The code of the error.
    - This might be platform-specific and differ between Android/iOS/Web
      
      ---
    
* <a id="joinTime"></a>joinTime - The time that it took the player to start active playback for the first time.
    - Obtained by calculating the time that passed from first PLAY_REQUEST to the PLAY event

    ---
  
* <a id="playTimeSum"></a>playTimeSum - Total of time played for the current KAVA session.
    - Should be in format of float (second.milliSecond)
    - Can be 0 to ∞
    - Only active playback should be counted
    - When a KAVA session expires/resets, it should be reset to the initial value = 0

    ---
    
* <a id="averageBitrate"></a>averageBitrate - Average of all [actualBitrate](#actualBitrate) for the current KAVA session
    - When a KAVA session expires/resets, it should be reset to the initial value = 0

    ---
    
* <a id="language"></a>language - Selected audio language

    ---
* <a id="caption"></a>caption - Selected caption language    



## <a id="common_params"></a>COMMON_PARAMS:

  - [eventType](#eventType)
  - [partnerId](#partnerId)
  - [entryId](#entryId)
  - [flavourId](#flavourId)
  - [sessionId](#sessionId)
  - [eventIndex](#eventIndex)
  - [ks](#ks)
  - [playbackContext](#playbackContext)
  - [referrer](#referrer)
  - [deliveryType](#deliveryType)
  - [playbackType](#playbackType)
  - [sessionStartTime](#sessionStartTime)
  - [uiConfId](#uiConfId)
  - [clientVer](#clientVer)
  - [position](#position)
  - [customVar1](#customVar1)
  - [customVar2](#customVar2)
  - [customVar3](#customVar3)
  
  
## <a id="endSessionResetParams"></a>Kava end session params to reset:
  - [eventIndex](#eventIndex) - Reset value = 1
  - [playTimeSum](#playTimeSum) - Reset value = 0
  - [averageBitrate](#averageBitrate) - Reset value = 0
  - [bufferTimeSum](#bufferTimeSum) - Reset value = 0
  
## <a id="serverResponse"></a>Server response Json structure:

The following is the structure of the server response:

```json
{
"time": 12345,
"viewEventsEnabled": true
}
```

## <a id="eventReportUniqueConditions"></a>Important event report conditions

There are some conditions that should be mentioned:

  - [View](#viewEvent) event that was not reported for 30 seconds considered by server as dead session. So client should reset all session related values (look [here](#viewEvent))
  - If server decides to disable [View](#viewEvent) event it will do it by changing flag of viewEventsEnabled field (in json response), Client MUST NOT reset any values, but just prevent [View](#viewEvent) event from sending.
  - When [Pause](#pauseEvent) event triggered it should reset[sessionStartTime](#sessionStartTime) in way that next event coming after pause will hold newly received [sessionStartTime](#sessionStartTime) value from server reposnse.