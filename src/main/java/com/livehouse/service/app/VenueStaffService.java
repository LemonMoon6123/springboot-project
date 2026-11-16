package com.livehouse.service.app;

import java.util.List;

public interface VenueStaffService {

    boolean isActiveStaffAtVenue(Long userId, Long venueId);

    void requireStaffAtVenue(Long userId, Long venueId);

    List<Long> listVenueIdsForStaff(Long userId);
}