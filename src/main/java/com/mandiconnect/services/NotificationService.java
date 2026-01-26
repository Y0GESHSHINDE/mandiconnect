package com.mandiconnect.services;

import com.mandiconnect.models.Farmer;
import com.mandiconnect.models.FarmerEntry;
import com.mandiconnect.models.Notification;
import com.mandiconnect.repositories.FarmerRepository;
import com.mandiconnect.repositories.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final FarmerRepository farmerRepository;

    /* =====================================================
       1️⃣ PRICE POSTED (Broadcast)
       ===================================================== */
    public void notifyPricePosted(FarmerEntry entry) {

        String cropId = entry.getCrop().getId();
        String marketId = entry.getMarket().getId();

        List<Farmer> farmers = farmerRepository.findAll();

        for (Farmer farmer : farmers) {

            // skip self
            if (farmer.getId().equals(entry.getFarmer().getId())) {
                continue;
            }

            if (!isInterestedFarmer(farmer, cropId, marketId)) {
                continue;
            }

            Notification notification = Notification.builder()
                    .userId(farmer.getId())
                    .type("PRICE_POSTED")
                    .title("New Price Posted")
                    .message(
                            "New price for "
                                    + entry.getCrop().getName()
                                    + " posted in "
                                    + entry.getMarket().getMarketName()
                    )
                    .cropId(cropId)
                    .marketId(marketId)
                    .isRead(false)
                    .createdAt(LocalDateTime.now())
                    .build();

            notificationRepository.save(notification);
        }
    }

    /* =====================================================
       2️⃣ PRICE AGREE
       ===================================================== */
    public void notifyPriceAgree(FarmerEntry entry, String voterFarmerId) {

        // do not notify if farmer votes on own price
        if (entry.getFarmer().getId().equals(voterFarmerId)) {
            return;
        }

        Notification notification = Notification.builder()
                .userId(entry.getFarmer().getId()) // ✅ RECEIVER
                .type("PRICE_AGREE")
                .title("Price Approved")
                .message(
                        "A farmer agreed with your "
                                + entry.getCrop().getName()
                                + " price in "
                                + entry.getMarket().getMarketName()
                )
                .cropId(entry.getCrop().getId())
                .marketId(entry.getMarket().getId())
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .build();

        notificationRepository.save(notification);
    }

    /* =====================================================
       3️⃣ PRICE DISAGREE
       ===================================================== */
    public void notifyPriceDisAgree(FarmerEntry entry, String voterFarmerId) {

        // do not notify if farmer votes on own price
        if (entry.getFarmer().getId().equals(voterFarmerId)) {
            return;
        }

        Notification notification = Notification.builder()
                .userId(entry.getFarmer().getId()) // ✅ RECEIVER
                .type("PRICE_DISAGREE")
                .title("Price Disagreed")
                .message(
                        "A farmer disagreed with your "
                                + entry.getCrop().getName()
                                + " price in "
                                + entry.getMarket().getMarketName()
                )
                .cropId(entry.getCrop().getId())
                .marketId(entry.getMarket().getId())
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .build();

        notificationRepository.save(notification);
    }

    /* =====================================================
       Helper
       ===================================================== */
    private boolean isInterestedFarmer(Farmer farmer, String cropId, String marketId) {

        if (farmer.getFarmDetails() == null) return false;
        if (farmer.getFarmDetails().getCropIds() == null) return false;
        if (farmer.getFarmDetails().getPreferredMarketIds() == null) return false;

        return farmer.getFarmDetails().getCropIds().contains(cropId)
                && farmer.getFarmDetails().getPreferredMarketIds().contains(marketId);
    }
}