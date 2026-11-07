package com.exaclast.centerpiece

import android.app.PendingIntent
import android.graphics.Bitmap
import android.os.Parcel
import android.os.Parcelable
import android.widget.RemoteViews

/**
 * Data payload for a status item hosted in the Centerpiece top bar.
 * Supports standard icon/text as well as arbitrary custom RemoteViews micro-UIs
 * for both the status bar chip and its expanded popover dropdown sheet.
 */
data class CenterpieceItem(
    val id: String,
    val packageName: String,
    val title: String,
    val subtitle: String? = null,
    val iconRes: Int = 0,
    val iconBitmap: Bitmap? = null,
    val priority: Int = 0,
    val clickIntent: PendingIntent? = null,
    val actions: List<CenterpieceAction> = emptyList(),
    val isBuiltIn: Boolean = false,
    val remoteViews: RemoteViews? = null,
    val popoverRemoteViews: RemoteViews? = null
) : Parcelable {

    constructor(parcel: Parcel) : this(
        id = parcel.readString() ?: "",
        packageName = parcel.readString() ?: "",
        title = parcel.readString() ?: "",
        subtitle = parcel.readString(),
        iconRes = parcel.readInt(),
        iconBitmap = parcel.readParcelable(Bitmap::class.java.classLoader, Bitmap::class.java),
        priority = parcel.readInt(),
        clickIntent = parcel.readParcelable(PendingIntent::class.java.classLoader, PendingIntent::class.java),
        actions = mutableListOf<CenterpieceAction>().apply {
            parcel.readTypedList(this, CenterpieceAction.CREATOR)
        },
        isBuiltIn = parcel.readByte() != 0.toByte(),
        remoteViews = parcel.readParcelable(RemoteViews::class.java.classLoader, RemoteViews::class.java),
        popoverRemoteViews = parcel.readParcelable(RemoteViews::class.java.classLoader, RemoteViews::class.java)
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(id)
        parcel.writeString(packageName)
        parcel.writeString(title)
        parcel.writeString(subtitle)
        parcel.writeInt(iconRes)
        parcel.writeParcelable(iconBitmap, flags)
        parcel.writeInt(priority)
        parcel.writeParcelable(clickIntent, flags)
        parcel.writeTypedList(actions)
        parcel.writeByte(if (isBuiltIn) 1 else 0)
        parcel.writeParcelable(remoteViews, flags)
        parcel.writeParcelable(popoverRemoteViews, flags)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<CenterpieceItem> {
        override fun createFromParcel(parcel: Parcel): CenterpieceItem {
            return CenterpieceItem(parcel)
        }

        override fun newArray(size: Int): Array<CenterpieceItem?> {
            return arrayOfNulls(size)
        }
    }
}
