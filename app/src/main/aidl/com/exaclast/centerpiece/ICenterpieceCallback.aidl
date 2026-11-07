package com.exaclast.centerpiece;

oneway interface ICenterpieceCallback {
    void onItemClicked(String itemId);
    void onActionClicked(String itemId, String actionId);
    void onPopoverDismissed(String itemId);
}
