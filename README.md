[![CI Status](https://travis-ci.org/kaltura/playkit-android-kava.svg?branch=develop)](https://travis-ci.org/kaltura/playkit-android-kava)
[ ![Download](https://api.bintray.com/packages/kaltura/android/kavaplugin/images/download.svg) ](https://bintray.com/kaltura/android/kavaplugin/_latestVersion)
[![License](https://img.shields.io/badge/license-AGPLv3-black.svg)](https://github.com/kaltura/playkit-android-kava/blob/master/LICENSE)
![Android](https://img.shields.io/badge/platform-android-green.svg)

# playkit-android-kava

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
