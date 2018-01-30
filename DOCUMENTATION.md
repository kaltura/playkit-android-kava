# Kava Documentation.

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

```
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
```

You can see that start using KAVA is simple and require only few lines of code.


## List of KAVA Events:

Here you can see the list of all available KAVA Events:

* VIEW
* [IMPRESSION](#impression)
* PLAY_REQUEST
* PLAY
* RESUME
* PLAY_REACHED_25_PERCENT
* PLAY_REACHED_50_PERCENT
* PLAY_REACHED_75_PERCENT
* PLAY_REACHED_100_PERCENT
* PAUSE
* REPLAY
* SEEK
* CAPTIONS
* SOURCE_SELECTED
* AUDIO_SELECTED
* FLAVOR_SWITCH
* ERROR

        
## KAVA events explanation:


Here we will see some explanation about each event. When does it sent and what parameters it have.

* <a id="impression"></a>IMPRESSION - Sent when MediaEntry is loaded(Player event LOADED_METADATA). It will be triggered only once per entry. 
    - Parameters to send: COMMON_PARAMS
    ---
    
* PLAY_REQUEST - Sent when play was requested by application(Player event PLAY received)
    - Parameters to send: COMMON_PARAMS
    ---
    
* PLAY - Sent when actual playback has been started for the first time (Player PLAYING event received).
    - Parameters to send: 
        - COMMON_PARAMS
        - bufferTime
        - totalBufferTime
        - actualBitrate
        - joinTime
    ---
    
* RESUME - Sent when actual playback has been resumed (!NOT for the first time. Player PLAYING event received).
    - Parameters to send:
        - COMMON_PARAMS
        - bufferTime
        - totalBufferTime
        - actualBitrate
    ---
    
* PLAY_REACHED_25_PERCENT - Sent when player reached 25% of the playback. No matter if by seeking or regular playback.
    - Sent only once per entry.
    - Parameters to send:
        - COMMON_PARAMS
    ---
    
* PLAY_REACHED_50_PERCENT - Sent when player reached 50% of the playback. No matter if by seeking or regular playback.
    - Sent only once per entry. 
    - If reached before 25% (by seeking or startFrom) first will fire:
        - PLAY_REACHED_25_PERCENT event.
    - Parameters to send:
        - COMMON_PARAMS
    
    ---

* PLAY_REACHED_75_PERCENT - Sent when player reached 75% of the playback. No matter if by seeking or regular playback.
    - Sent only once per entry. 
    - If reached before 50% (by seeking or startFrom) first will fire:
        - PLAY_REACHED_25_PERCENT
        - PLAY_REACHED_50_PERCENT
    - Parameters to send:
        - COMMON_PARAMS
    ---
    
* PLAY_REACHED_100_PERCENT - Sent when player reached 100% of the playback(Player END event).
No matter if by seeking or regular playback.
    - Sent only once per entry.
    - If reached before 75% (by seeking or startFrom) first will fire: 
        - PLAY_REACHED_25_PERCENT
        - PLAY_REACHED_50_PERCENT
        - PLAY_REACHED_75_PERCENT
    - Parameters to send:
        - COMMON_PARAMS
    ---
    
* PAUSE - Sent when playback was paused (Player PAUSE event received).
    - During pause Kava should prevent from counting VIEW event timer.
    - Parameters to send:
        - COMMON_PARAMS
    ---
    
* REPLAY - Sent when replay called by application (Player REPLAY event received).
    - Replay should reset all the parameters related to playback except PLAYER_REACHED... events.
    - Parameters to send:
        - COMMON_PARAMS
    ---
    
* SEEK - Sent when seek requested. (Player SEEKING event received).
    - Parameters to send:
        - COMMON_PARAMS
        - targetPosition
    ---
    
* SOURCE_SELECTED - Sent when video track changed manually (Not ABR selection. Player VIDEO_TRACK_CHANGED event received).
    - Parameters to send:
        - COMMON_PARAMS
        - actualBitrate
    ---
    
* FLAVOR_SWITCH - Sent when video flavour changed by ABR mode (Player PLAYBACK_INFO_UPDATED event received)     

    - Newly received bitrate != actualBitrate
    - Parameters to send:
        - COMMON_PARAMS
        - actualBitrate
    ---
    
* AUDIO_SELECTED - Sent when audio track changed (Player AUDIO_TRACK_CHANGED event received).
    - Parameters to send:
        - COMMON_PARAMS
        - language
    ---
    
* CAPTIONS - Sent when text track changed. (Player TEXT_TRACK_CHANGED event received).
  - Parameters to send:
    - COMMON_PARAMS
    - caption
    ---
* ERROR - Sent when error occurs. (Player ERROR event received).
    - Parameters to send:
        - COMMON_PARAMS
        - errorCode
    ---
    
* VIEW - Collective event that represent report for every 10 seconds of active playback.
    - Sent every 10 second of active playback(when player is paused, view timer should be paused/stopped).
    - 30 seconds without VIEW event will reset KAVA session, so all the VIEW specific parameters should be reset also.
    - Server can notify Kava (via response field 'viewEventsEnabled' = false) to shut down VIEW events.
When it happens, VIEW events will be blocked from sending until server decides to enable VIEW events again. 
    - Parameters to send:
        - COMMON_PARAMS
        - bufferTime
        - totalBufferTime
        - actualBitrate
        - playTimeSum
        - averageBitrate

## KAVA Parameters:

Kava parameters are additional data that is sent with Kava event and represent relevant information about current playback, media information etc


* eventType - id of the KavaEvent.
    - In Android obtained from KavaEvent enum. For example IMPRESSION(1), VIEW(99) etc.
    - **Mandatory field**. If not exist, sending of all events should be blocked and related warning should be printed to log.
    ---
    
* partnerId - The partner account ID on Kaltura's platform.
    - Obtained from pluginConfig object.
    - **Mandatory field**. If not exist, sending of all events should be blocked and related warning should be printed to log.
		
    ---

* entryId - The delivered content ID on Kaltura's platform.
    - Obtained from mediaConfig object.
    - **Mandatory field**. If not exist, sending of all events should be blocked and related warning should be printed to log.
    
    ---
    
* flavourId - The ID of the flavour that is currently displayed (for future use, will not be used in aggregations) **!!!NOT_SUPPORTED on Mobile!!!**

    ---
    
* ks - The Kaltura encoded session data.
    - Obtained from pluginConfig object.
    - If not exist do not send this parameter at all.
    
    ---
    
* sessionId - A unique string which identifies a unique viewing session, a page refresh should use different identifier.
    - Obtained from player (player.getSessionId()). 
    
    ---

* eventIndex - A sequence number which describe the order of events in a viewing session. In general it is just a counter of sent events.
    - Starts from 1.
    - Each event that is send must have unique id and increment after each event sent.
    - When Kava session expired/reset should be reset to the initial value (1). For example: 
      - New media entry will reset this counter. 
      - VIEW event that was not sent for 30 seconds also reset this value.
   
    ---
  
* bufferTime - The amount time spent on buffering from the last VIEW event.
    - Should be 0 to 10 in VIEW events.
    - Can be 0 to ∞ for PLAY/RESUME events.
    - Should be in format of float (second.milliSecond).
    - Reset to 0 after every VIEW event.
    - New media entry will reset this value.
    - VIEW event that was not sent for 30 seconds also reset this value.

    ---
  
* bufferTimeSum - Sum of all the buffer time during all the playback.
    - Can be 0 to ∞.
    - Should be in format of float (second.milliSecond).
    - New media entry will reset this value.
    - VIEW event that was not sent for 30 seconds also reset this value.
    
    ---

* actualBitrate - The bitrate of the displayed video track in kbps.
    - When ABR mode selected manually - value should be 0.
    - In SOURCE_SELECTED event should be equal to the manually selected video bitrate.
    - In FLAVOUR_SWITCH event should be based on ABR selection and equal to the currently selected bitrate by ABR.

    ---

* referrer - Application referrer id.
    - Obtained from pluginConfig.
    - Valid referrer should start from one of the following prefixes:
      - "app://"
      - "http://"
      - "https://"
    - In case pluginConfig have no referrer or referrer is in invalid format, default one should be build using following format:
      - application id with prefix of (app://). For example: app://com.kaltura.player
    - Should be converted to Base64 (before sending to the server).
    
    ---

* deliveryType - The player's streamer type.
    - Obtained from pkMediaSource.getMediaFormat(), when Player SOURCE_SELECTED event received.
    - Should be re-obtained for every new media entry.
    - Should be one of the following:
      - hls
      - dash
      - url (if format explicitly not mentioned or differs from previous two)
    ---
  
* playbackType - The type of the current playback.
    - Initially obtained from mediaConfig.getMediaEntry().getMediaType(). If for some reason value could not be obtained, we will take it from player. 
    - Must be on of the following:
      - vod - for regular media.
      - live - for live stream.
      - dvr - when playback type is 'live' and offset from the live edge is grater then threshold that specified in KavaAnalyticsConfig object (default is 2 minutes, but can be customized).
 
    ---

* sessionStartTime - The timestamp of the first event in the session.
    - Obtained from response of the first event. "time" field on Json object that comes with response.
    - First event the fired will not have this value.
    - Should be in Unix time format.
    
    ---

* uiConfId - The player ui configuration id.
      - Obtained from pluginConfig object.
      - If not exist do not send this parameter at all.

    ---
  
* clientVer - The player version (PlayKitManager.CLIENT_TAG)

    ---
    
* clientTag - TODO

    ---
  
* position - The playback position of the media.
      - Should be in format of float (second.milliSecond).
      - When playbackType is = live, this value should represent the offset of the playback position from the live edge.
          - 0 when media position is on the live edge
          - -2.5 when offset from the live edge is 2.5 seconds.
      - Should be positive value for vod playbackType.

    ---
  
* playbackContext - The category id describing the current played context.
      - Optional parameter.
      - Obtained from pluginConfig.
      - If not exist do not send this parameter at all.

    ---

* customVar1 - Optional parameter defined by the user.
      - Can be any primitive value or String.
      - Optional parameter.
      - Obtained from pluginConfig.
      - If not exist do not send this parameter at all.
  
    ---

* customVar2 - Optional parameter defined by the user.
      - Can be any primitive value or String.
      - Optional parameter.
      - Obtained from pluginConfig.
      - If not exist do not send this parameter at all.

    ---
    
* customVar3 - Optional parameter defined by the user.
      - Can be any primitive value or String.
      - Optional parameter.
      - Obtained from pluginConfig.
      - If not exist do not send this parameter at all.

    ---
  
* targetPosition - The requested seek position of the media. 
      - Should be in format of float (second.milliSecond).
      - Obtained from player SEEKING event.

    ---
  
* errorCode - The code of the occurred error.
    - Might be platform specific and deffer between Android/iOS/Web
      
      ---
  
* hasKanalony - TODO

    ---
    
* joinTime - Time that took to player start active playback for the first time.
      - Obtained by calculating time that passed from first PLAY_REQUEST to PLAY event.

    ---
  
* kalsig - TODO

    ---
    
* playTimeSum - Sum of time played for the current Kava session.
    - Should be in format of float (second.milliSecond).
    - Can be 0 to ∞.
    - When Kava session expired/reset should be reset to the initial value (1). For example: 

    ---
    
* averageBitrate - Sum of all actualBitrate for the current Kava session.


## Kava COMMON_PARAMS:

  - eventType
  - partnerId
  - entryId
  - flavourId
  - sessionId
  - eventIndex
  - ks
  - playbackContext
  - referrer
  - deliveryType
  - playbackType
  - kalsig
  - sessionStartTime
  - uiConfId
  - clientVer
  - clientTag
  - position
  - customVar1
  - customVar2
  - customVar3


    

 
    