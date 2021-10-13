//
// Created by andrey on 5/28/18.
//

#ifndef TRANSMISSIONBTC_NATIVE_TO_JAVA_H
#define TRANSMISSIONBTC_NATIVE_TO_JAVA_H

extern "C" {
void callAddedOrChangedCallback();

void callStoppedCallback();

void callSessionChangedCallback();

void callScheduledAltSpeedCallback();

} // extern "C"
#endif //TRANSMISSIONBTC_NATIVE_TO_JAVA_H
