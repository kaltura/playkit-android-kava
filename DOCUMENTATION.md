# Kava Documentation.

## Table of Contents:

* [About Kava](#about-kava)
* [Kava Integration](#kava-integration)
* [Plugin configurations](#plugin-configurations)
* [Plugin configuration fields](#plugin-configuration-fields)
* [List of KAVA Events](#list-of-kava-events)
* [KAVA events explanation](#kava-events-explanation)
* [KAVA Parameters](#kava-parameters)
* [COMMON_PARAMS](#common_params)
* [Kava end session params to reset](#kava-end-session-params-to-reset)
* [Server response Json structure](#server-response-json-structure)

## About Kava:

Kava is abbreviation for Kaltura Advanced Video Analytics. The main purpose of this plugin is to track and collect
various events and data about Playkit video player. Integration is pretty simple and can be achieved during those simple steps.

## Kava Integration:
Kava build on top of Kaltura Playkit SDK so to get started you should add dependencies in your application build.gradle file.
Note, Playkit is already included in KAVAPlugin.

```
dependencies {

    implementation 'com.kaltura:playkit-android-kava:XXX' //instead of XXX use latest version. 
   
}

repositories {
    maven { url 'https://jitpack.io' }
}
```

Now lets see how to use KAVAPlugin in your application

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

You can see that start using KAVA is simple and require only few lines of code.


## Plugin configurations:

Like other Kaltura`s Playkit plugins, Kava have a configurations that can be used by application. 
In following code snippet we will see how to configure Kava with custom parameters. Below you will have detailed explanation of each field and it default values.
Note, you can use KavaAnalyticsConfig object or build Json with your configurations. 

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

## Plugin configuration fields:

* [partnerId](#partnerId) - your Kaltura partnerId
    * Mandatory field. Without it KavaAnalyticsPlugin will not be able to perform.
    
* baseUrl - base Url where KavaAnalytics events will be sent.
    * Default value - http://analytics.kaltura.com/api_v3/index.php
    * Optional field
    
* [uiconfId](#uiconfId) - id of the Kaltura UI configurations.
    * Optional field

* [ks](#ks) - your Kaltura ks.
    * Optional field

* [playbackContext](#playbackContext) - you can provide your own custom context for the media playback.
This is used to send the id of the category from which the user is playing the entry.
    * Optional field
        
* [referrer](#referrer) - your referrer.
    * Default value - "app://" + your application package name.
    * Optional field
    
* dvrThreshold - threshold from the live edge. 
When player`s playback position from the live edge <= then dvrThreshold, Kava will set [playbackType](#playbackType) to dvr. Otherwise it will be live.
    * Use milliseconds for this field.
    * Default value - 120000 (2 minutes)
    * Optional field
    
* [customVar1](#customVar1), [customVar2](#customVar2), [customVar3](#customVar3) - you can use this fields for your own custom needs. 

    * Optional field
    
    
## List of KAVA Events:

Here you can see the list of all available KAVA Events:

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

        
## KAVA events explanation:


Here we will see some explanation about each event. When does it sent and what parameters it have.


* <a id="viewEvent"></a>VIEW - Collective event that represent report for every 10 seconds of active playback.
    - eventId = 99
    - Sent every 10 second of active playback(when player is paused, view timer should be paused/stopped).
    - 30 seconds without VIEW event will reset KAVA session, so all the VIEW [specific parameters](#endSessionResetParams) should be reset also.
    - Server may notify Kava (via response field ["viewEventsEnabled" = false](#serverResponse)) to shut down VIEW events.
When it happens, VIEW events should be blocked from sending until server decides to enable VIEW events again. 
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
    
* <a id="playRequestEvent"></a>PLAY_REQUEST - Sent when play was requested by application(Player event PLAY received)
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
    - During pause Kava should prevent from counting VIEW event timer.
    - Parameters to send:
        - [COMMON_PARAMS](#common_params)
    ---
    
* <a id="replayEvent"></a>REPLAY - Sent when replay called by application (Player REPLAY event received).
    - eventId = 34
    - Replay should reset all the parameters related to playback except PLAYER_REACHED... events.
    - Parameters to send:
        - [COMMON_PARAMS](#common_params)
    ---
    
* <a id="seekEvent"></a>SEEK - Sent when seek requested. (Player SEEKING event received).
    - eventId = 35
    - Parameters to send:
        - [COMMON_PARAMS](#common_params)
        - [targetPosition](#targetPosition)
    
    ---
    
* <a id="play25Event"></a>PLAY_REACHED_25_PERCENT - Sent when player reached 25% of the playback. No matter if by seeking or regular playback.
    - eventId = 11
    - Sent only once per entry.
    - Parameters to send:
        - [COMMON_PARAMS](#common_params)
    ---
    
* <a id="play50Event"></a>PLAY_REACHED_50_PERCENT - Sent when player reached 50% of the playback. No matter if by seeking or regular playback.
    - eventId = 12
    - Sent only once per entry. 
    - If reached before 25% (by seeking or startFrom) first will fire:
        - PLAY_REACHED_25_PERCENT event.
    - Parameters to send:
        - [COMMON_PARAMS](#common_params)
    
    ---

* <a id="play75Event"></a>PLAY_REACHED_75_PERCENT - Sent when player reached 75% of the playback. No matter if by seeking or regular playback.
    - eventId = 13
    - Sent only once per entry. 
    - If reached before 50% (by seeking or startFrom) first will fire:
        - PLAY_REACHED_25_PERCENT
        - PLAY_REACHED_50_PERCENT
    - Parameters to send:
        - [COMMON_PARAMS](#common_params)
    ---
    
* <a id="play100Event"></a>PLAY_REACHED_100_PERCENT - Sent when player reached 100% of the playback(Player END event).
No matter if by seeking or regular playback.
    - eventId = 14
    - Sent only once per entry.
    - If reached before 75% (by seeking or startFrom) first will fire: 
        - PLAY_REACHED_25_PERCENT
        - PLAY_REACHED_50_PERCENT
        - PLAY_REACHED_75_PERCENT
    - Parameters to send:
        - [COMMON_PARAMS](#common_params)
    ---
    
* <a id="sourceSelectedEvent"></a>SOURCE_SELECTED - Sent when video track changed manually (Not ABR selection. Player VIDEO_TRACK_CHANGED event received).
    - eventId = 39
    - Parameters to send:
        - [COMMON_PARAMS](#common_params)
        - [actualBitrate](#actualBitrate)
    ---
    
* <a id="flavourSwitchEvent"></a>FLAVOR_SWITCH - Sent when video flavour changed by ABR mode (Player PLAYBACK_INFO_UPDATED event received)     
    - eventId = 43
    - Newly received bitrate != [actualBitrate](#actualBitrate)
    - Parameters to send:
        - [COMMON_PARAMS](#common_params)
        - [actualBitrate](#actualBitrate)
    ---
    
* <a id="audioSelectedEvent"></a>AUDIO_SELECTED - Sent when audio track changed (Player AUDIO_TRACK_CHANGED event received).
    - eventId = 42
    - Parameters to send:
        - [COMMON_PARAMS](#common_params)
        - [language](#language)
    ---
    
* <a id="captionsEvent"></a>CAPTIONS - Sent when text track changed. (Player TEXT_TRACK_CHANGED event received).
    - eventId = 38
    - Parameters to send:
        - [COMMON_PARAMS](#common_params)
        - [caption](#caption)
    ---
* <a id="errorEvent"></a>ERROR - Sent when error occurs. (Player ERROR event received).
    - eventId = 98
    - Parameters to send:
        - [COMMON_PARAMS](#common_params)
        - [errorCode](#errorCode)


## KAVA Parameters:

Kava parameters are additional data that is sent with Kava event and represent relevant information about current playback, media information etc


* <a id="eventType"></a>eventType - id of the KavaEvent.
    - In Android obtained from KavaEvent enum. For example IMPRESSION(1), VIEW(99) etc.
    - **Mandatory field**. If not exist, sending of all events should be blocked and related warning should be printed to log.
    ---
    
* <a id="partnerId"></a>partnerId - The partner account ID on Kaltura's platform.
    - Obtained from pluginConfig object.
    - **Mandatory field**. If not exist, sending of all events should be blocked and related warning should be printed to log.
		
    ---

* <a id="entryId"></a>entryId - The delivered content ID on Kaltura's platform.
    - Obtained from mediaConfig object.
    - **Mandatory field**. If not exist, sending of all events should be blocked and related warning should be printed to log.
    
    ---
    
* <a id="flavourId"></a>flavourId - The ID of the flavour that is currently displayed (for future use, will not be used in aggregations) **!!!NOT_SUPPORTED on Mobile!!!**

    ---
    
* <a id="ks"></a>ks - The Kaltura encoded session data.
    - Obtained from pluginConfig object.
    - If not exist do not send this parameter at all.
    
    ---
    
* <a id="sessionId"></a>sessionId - A unique string which identifies a unique viewing session, a page refresh should use different identifier.
    - Obtained from player (player.getSessionId()). 
    
    ---

* <a id="eventIndex"></a>eventIndex - A sequence number which describe the order of events in a viewing session. In general it is just a counter of sent events.
    - Starts from 1.
    - Each event that is send must have unique id and increment after each event sent.
    - When Kava session expired/reset should be reset to the initial value (1). For example: 
      - New media entry will reset this counter. 
      - VIEW event that was not sent for 30 seconds also reset this value.
   
    ---
  
* <a id="bufferTime"></a>bufferTime - The amount time spent on buffering from the last VIEW event.
    - Should be 0 to 10 in VIEW events.
    - Can be 0 to ∞ for PLAY/RESUME events.
    - Should be in format of float (second.milliSecond).
    - Reset to 0 after every VIEW event.
    - New media entry will reset this value.
    - VIEW event that was not sent for 30 seconds also reset this value.

    ---
  
* <a id="bufferTimeSum"></a>bufferTimeSum - Sum of all the buffer time during all the playback.
    - Can be 0 to ∞.
    - Should be in format of float (second.milliSecond).
    - New media entry will reset this value.
    - VIEW event that was not sent for 30 seconds also reset this value.
    
    ---

* <a id="actualBitrate"></a>actualBitrate - The bitrate of the displayed video track in kbps.
    - When ABR mode selected manually - value should be 0.
    - In SOURCE_SELECTED event should be equal to the manually selected video bitrate.
    - In FLAVOUR_SWITCH event should be based on ABR selection and equal to the currently selected bitrate by ABR.

    ---

* <a id="referrer"></a>referrer - Application referrer id.
    - Obtained from pluginConfig.
    - Valid referrer should start from one of the following prefixes:
      - "app://"
      - "http://"
      - "https://"
    - In case pluginConfig have no referrer or referrer is in invalid format, default one should be build using following format:
      - application id with prefix of (app://). For example: app://com.kaltura.player
    - Should be converted to Base64 (before sending to the server).
    
    ---

* <a id="deliveryType"></a>deliveryType - The player's streamer type.
    - Obtained from pkMediaSource.getMediaFormat(), when Player SOURCE_SELECTED event received.
    - Should be re-obtained for every new media entry.
    - Should be one of the following:
      - hls
      - dash
      - url (if format explicitly not mentioned or differs from previous two)
    ---
  
* <a id="playbackType"></a>playbackType - The type of the current playback.
    - Initially obtained from mediaConfig.getMediaEntry().getMediaType(). If for some reason value could not be obtained, we will take it from player. 
    - Must be on of the following:
      - vod - for regular media.
      - live - for live stream.
      - dvr - when playback type is 'live' and offset from the live edge is grater then threshold that specified in KavaAnalyticsConfig object (default is 2 minutes, but can be customized).
 
    ---

* <a id="sessionStartTime"></a>sessionStartTime - The timestamp of the first event in the session.
    - Obtained from response of the first event. ["time"](#server-response-json-structure) field on Json object that comes with response.
    - First event the fired will not have this value.
    - Should be in Unix time format.
    
    ---

* <a id="uiConfId"></a>uiConfId - The player ui configuration id.
    - Obtained from pluginConfig object.
    - If not exist do not send this parameter at all.

    ---
  
* <a id="clientVer"></a>clientVer - The player version (PlayKitManager.CLIENT_TAG)

    ---
    
* <a id="clientTag"></a>clientTag - TODO

    ---
  
* <a id="position"></a>position - The playback position of the media.
    - Should be in format of float (second.milliSecond).
    - When [playbackType](#playbackType) is = live, this value should represent the offset of the playback position from the live edge.
        - 0 when media position is on the live edge
        - -2.5 when offset from the live edge is 2.5 seconds.
    - Should be positive value for vod [playbackType](#playbackType).

    ---
  
* <a id="playbackContext"></a>playbackContext - The category id describing the current played context.
    - Optional parameter.
    - Obtained from pluginConfig.
    - If not exist do not send this parameter at all.

    ---

* <a id="customVar1"></a>customVar1, <a id="customVar2"></a>customVar2, <a id="customVar3"></a>customVar3 - Optional parameter defined by the user.
    - Can be any primitive value or String.
    - Optional parameter.
    - Obtained from pluginConfig.
    - If not exist do not send this parameter at all.
  
    ---
  
* <a id="targetPosition"></a>targetPosition - The requested seek position of the media. 
    - Should be in format of float (second.milliSecond).
    - Obtained from player SEEKING event.

    ---
  
* <a id="errorCode"></a>errorCode - The code of the occurred error.
    - Might be platform specific and deffer between Android/iOS/Web
      
      ---
    
* <a id="joinTime"></a>joinTime - Time that took to player start active playback for the first time.
    
    - Obtained by calculating time that passed from first PLAY_REQUEST to PLAY event.

    ---
  
* <a id="playTimeSum"></a>playTimeSum - Sum of time played for the current Kava session.
    - Should be in format of float (second.milliSecond).
    - Can be 0 to ∞.
    - Only active playback should be counted.
    - When Kava session expired/reset should be reset to the initial value = 0. 

    ---
    
* <a id="averageBitrate"></a>averageBitrate - Average of all [actualBitrate](#actualBitrate) for the current Kava session.
    - When Kava session expired/reset should be reset to the initial value = 0.

    ---
    
* <a id="language"></a>language - Selected audio language.

    ---
* <a id="caption"></a>caption - Selected caption language.    



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
  - [clientTag](#clientTag)
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

Following is the structure of server response:

```json
{
"time": 12345,
"viewEventsEnabled": true
}
```


    

 
    