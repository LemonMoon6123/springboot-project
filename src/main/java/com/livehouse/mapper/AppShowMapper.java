package com.livehouse.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface AppShowMapper {

    long countShowList(@Param("city") String city, @Param("type") String type,
                       @Param("date") String date, @Param("venueId") Long venueId,
                       @Param("artistId") Long artistId);

    List<Map<String, Object>> selectShowList(@Param("city") String city, @Param("type") String type,
                                             @Param("date") String date, @Param("venueId") Long venueId,
                                             @Param("artistId") Long artistId, @Param("sort") String sort,
                                             @Param("offset") long offset, @Param("size") long size);

    List<String> selectDistinctCities();

    List<String> selectDistinctTypes();
}