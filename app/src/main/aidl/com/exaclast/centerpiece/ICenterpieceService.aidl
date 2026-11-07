package com.exaclast.centerpiece;

import com.exaclast.centerpiece.CenterpieceItem;
import com.exaclast.centerpiece.ICenterpieceCallback;

interface ICenterpieceService {
    boolean registerItem(in CenterpieceItem item, in ICenterpieceCallback callback);
    boolean updateItem(in CenterpieceItem item);
    boolean unregisterItem(String itemId);
    List<CenterpieceItem> getItems();
}
