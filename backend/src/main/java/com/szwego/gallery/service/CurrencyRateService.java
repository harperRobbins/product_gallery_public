package com.szwego.gallery.service;

import com.szwego.gallery.dto.CurrencyRateSnapshotVO;

public interface CurrencyRateService {
    CurrencyRateSnapshotVO getLatestSnapshot();

    void refreshLatestRates();
}
