import { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
    appId: 'com.logseq.app',
    appName: 'Logseq',
    bundledWebRuntime: false,
    webDir: 'public',
    plugins: {
        SplashScreen: {
            launchShowDuration: 3000,
            launchAutoHide: false,
            androidScaleType: "CENTER_CROP",
            splashImmersive: false,
            backgroundColor: "#002b36"
        },
    },
    ios: {
        scheme: "Logseq"
    }
    // do not commit this into source control
    // source: https://capacitorjs.com/docs/guides/live-reload
    , server: {
        // url: "http://192.168.10.109:3001",
        //      url: "http://192.168.31.246:3001",
        url: "http://10.233.233.114:3001",
        cleartext: true
    }
};

export = config;
