package com.vkaan.runtimeinspector;

interface IInspector {
    // The event travels inside the Bundle so AIDL never needs a CREATOR for the sealed type.
    oneway void onEvent(in Bundle event);

    // Pull the platform log dump and run the card service rules over it.
    oneway void inspect();
}
