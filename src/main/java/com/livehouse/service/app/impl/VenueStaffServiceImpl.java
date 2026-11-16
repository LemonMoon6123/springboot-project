package com.livehouse.service.app.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.livehouse.entity.StatusCode;
import com.livehouse.entity.VenueStaff;
import com.livehouse.exception.StatusCodeException;
import com.livehouse.mapper.VenueStaffMapper;
import com.livehouse.service.app.VenueStaffService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class VenueStaffServiceImpl implements VenueStaffService {

    @Resource
    private VenueStaffMapper venueStaffMapper;

    @Override
    public boolean isActiveStaffAtVenue(Long userId, Long venueId) {
        if (userId == null || venueId == null) {
            return false;
        }
        return venueStaffMapper.selectCount(new LambdaQueryWrapper<VenueStaff>()
                .eq(VenueStaff::getUserId, userId)
                .eq(VenueStaff::getVenueId, venueId)
                .eq(VenueStaff::getStatus, 1)) > 0;
    }

    @Override
    public void requireStaffAtVenue(Long userId, Long venueId) {
        if (!isActiveStaffAtVenue(userId, venueId)) {
            throw new StatusCodeException(StatusCode.VERIFY_NO_PERMISSION);
        }
    }

    @Override
    public List<Long> listVenueIdsForStaff(Long userId) {
        return venueStaffMapper.selectList(new LambdaQueryWrapper<VenueStaff>()
                        .eq(VenueStaff::getUserId, userId)
                        .eq(VenueStaff::getStatus, 1))
                .stream().map(VenueStaff::getVenueId).collect(Collectors.toList());
    }
}