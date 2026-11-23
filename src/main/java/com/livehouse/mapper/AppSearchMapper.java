package com.livehouse.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.livehouse.vo.app.search.HotArtistVo;
import com.livehouse.vo.app.search.SearchArtistRowVo;
import com.livehouse.vo.app.search.SearchVenueItemVo;
import com.livehouse.vo.app.search.WantRankItemVo;
import com.livehouse.vo.app.show.ShowCardVo;

import java.util.List;

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

    List<ShowCardVo> selectSearchShows(@Param("keyword") String keyword,
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

    List<SearchArtistRowVo> selectSearchArtists(@Param("keyword") String keyword,
                                                @Param("offset") long offset,
                                                @Param("size") long size);

    long countSearchVenues(@Param("keyword") String keyword, @Param("city") String city);

    List<SearchVenueItemVo> selectSearchVenues(@Param("keyword") String keyword,
                                               @Param("city") String city,
                                               @Param("offset") long offset,
                                               @Param("size") long size);

    List<WantRankItemVo> selectWantRankTop3();

    List<HotArtistVo> selectHotArtists(@Param("limit") int limit);
}