package com.livehouse.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface AppSearchMapper {

    long countSearchShows(@Param("keyword") String keyword,
                          @Param("city") String city,
                          @Param("artistId") Long artistId,
                          @Param("timeStart") LocalDateTime timeStart,
                          @Param("timeEnd") LocalDateTime timeEnd,
                          @Param("priceMin") BigDecimal priceMin,
                          @Param("priceMax") BigDecimal priceMax,
                          @Param("hasTicket") Integer hasTicket);

    List<Map<String, Object>> selectSearchShows(@Param("keyword") String keyword,
                                                @Param("city") String city,
                                                @Param("artistId") Long artistId,
                                                @Param("timeStart") LocalDateTime timeStart,
                                                @Param("timeEnd") LocalDateTime timeEnd,
                                                @Param("priceMin") BigDecimal priceMin,
                                                @Param("priceMax") BigDecimal priceMax,
                                                @Param("hasTicket") Integer hasTicket,
                                                @Param("sort") String sort,
                                                @Param("offset") long offset,
                                                @Param("size") long size);

    long countSearchArtists(@Param("keyword") String keyword);

    List<Map<String, Object>> selectSearchArtists(@Param("keyword") String keyword,
                                                    @Param("offset") long offset,
                                                    @Param("size") long size);

    long countSearchVenues(@Param("keyword") String keyword, @Param("city") String city);

    List<Map<String, Object>> selectSearchVenues(@Param("keyword") String keyword,
                                                  @Param("city") String city,
                                                  @Param("offset") long offset,
                                                  @Param("size") long size);

    List<Map<String, Object>> selectWantRankTop3();
}