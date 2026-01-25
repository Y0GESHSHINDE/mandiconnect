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

    // Call after FarmerEntry save
    public void notifyPricePosted(FarmerEntry entry) {

        String cropId = entry.getCrop().getId();
        String marketId = entry.getMarket().getId();

        List<Farmer> farmers = farmerRepository.findAll();

        for (Farmer farmer : farmers) {

            // Skip self
            if (farmer.getId().equals(entry.getFarmer().getId())) {
                continue;
            }

            if (!isInterestedFarmer(farmer, cropId, marketId)) {
                continue;
            }

            Notification notification = Notification.builder()
                    .userId(farmer.getId())
                    .type("PRICE_POSTED")
                    .title("New Price Update")
                    .message("New price posted")   // 👈 generic message
                    .cropId(cropId)
                    .marketId(marketId)
                    .isRead(false)
                    .createdAt(LocalDateTime.now())
                    .build();

            notificationRepository.save(notification);
        }
    }

    private boolean isInterestedFarmer(Farmer farmer, String cropId, String marketId) {

        if (farmer.getFarmDetails() == null) return false;
        if (farmer.getFarmDetails().getCropIds() == null) return false;
        if (farmer.getFarmDetails().getPreferredMarketIds() == null) return false;

        return farmer.getFarmDetails().getCropIds().contains(cropId)
                && farmer.getFarmDetails().getPreferredMarketIds().contains(marketId);
    }
}