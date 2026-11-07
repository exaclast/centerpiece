package com.exaclast.centerpiece

import android.app.PendingIntent
import android.os.Parcel
import android.os.Parcelable

/**
 * Represents an actionable menu item inside a Centerpiece item's popover sheet.
 */
data class CenterpieceAction(
    val id: String,
    val label: String,
    val iconRes: Int = 0,
    val pendingIntent: PendingIntent? = null
) : Parcelable {

    constructor(parcel: Parcel) : this(
        id = parcel.readString() ?: "",
        label = parcel.readString() ?: "",
        iconRes = parcel.readInt(),
        pendingIntent = parcel.readParcelable(PendingIntent::class.java.classLoader, PendingIntent::class.java)
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(id)
        parcel.writeString(label)
        parcel.writeInt(iconRes)
        parcel.writeParcelable(pendingIntent, flags)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<CenterpieceAction> {
        override fun createFromParcel(parcel: Parcel): CenterpieceAction {
            return CenterpieceAction(parcel)
        }

        override fun newArray(size: Int): Array<CenterpieceAction?> {
            return arrayOfNulls(size)
        }
    }
}
