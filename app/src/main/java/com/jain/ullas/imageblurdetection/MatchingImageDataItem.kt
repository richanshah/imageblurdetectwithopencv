package com.jain.ullas.imageblurdetection

import android.graphics.Bitmap
import android.net.Uri

data class MatchingImageDataItem(
    var imgId: Int = 0,
    var imageBitmap: Bitmap? = null,
    var matchImageUri: Uri? = null,
    var imagePath: String = "",
    var matchImageName: String = "",
    var imageDiffPerc: Double = 0.0,
    var isMatch: Boolean = false,

    /*todo meta-data details*/
    var mTAG_IMAGE_UNIQUE_ID: String = "",

    var mTAG_GPS_LATITUDE: String = "",
    var mTAG_GPS_LONGITUDE: String = "",

    var mTAG_DATETIME_ORIGINAL: String = "",

    var mTAG_IMAGE_HEIGHT: String = "",
    var mTAG_IMAGE_WIDTH: String = "",

    var mTAG_SHUTTER_SPEED_VALUE: String = "",
    var mTAG_APERTURE_VALUE: String = "",
    var mTAG_BRIGHTNESS_VALUE: String = "",

    var hashValue: String = "",
)