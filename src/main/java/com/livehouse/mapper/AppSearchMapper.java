package com.livehouse.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface AppSearchMapper {

    @Select("<script>"
            + "SELECT COUNT(DISTINCT s.id) FROM tb_show s "
            + "INNER JOIN tb_performance p ON p.id = s.performance_id "
            + "INNER JOIN tb_venue v ON v.id = s.venue_id "
            + "WHERE p.status = 2 AND s.status IN (2, 3) "
            + "AND (s.end_time IS NULL OR s.end_time &gt; NOW()) "
            + "<if test='keyword != null and keyword != \"\"'>"
            + " AND (p.title LIKE CONCAT('%',#{keyword},'%') OR p.artist_name LIKE CONCAT('%',#{keyword},'%') "
            + " OR v.name LIKE CONCAT('%',#{keyword},'%')) "
            + "</if>"
            + "<if test='city != null and city != \"\"'> AND v.city = #{city} </if>"
            + "<if test='artistId != null'> AND p.artist_id = #{artistId} </if>"
            + "<if test='timeStart != null'> AND s.start_time &gt;= #{timeStart} </if>"
            + "<if test='timeEnd != null'> AND s.start_time &lt; #{timeEnd} </if>"
            + "<if test='priceMin != null'> AND COALESCE(s.min_price, p.min_price) &gt;= #{priceMin} </if>"
            + "<if test='priceMax != null'> AND COALESCE(s.min_price, p.min_price) &lt;= #{priceMax} </if>"
            + "<if test='hasTicket != null and hasTicket == 1'>"
            + " AND EXISTS (SELECT 1 FROM tb_ticket_type tt WHERE tt.show_id = s.id AND tt.status = 1 AND tt.left_stock &gt; 0)"
            + "</if>"
            + "</script>")
    long countSearchShows(@Param("keyword") String keyword, @Param("city") String city,
                          @Param("artistId") Long artistId,
                          @Param("timeStart") LocalDateTime timeStart, @Param("timeEnd") LocalDateTime timeEnd,
                          @Param("priceMin") java.math.BigDecimal priceMin, @Param("priceMax") java.math.BigDecimal priceMax,
                          @Param("hasTicket") Integer hasTicket);

    @Select("<script>"
            + "SELECT s.id AS showId, p.id AS performanceId, "
            + "COALESCE(p.title, s.title) AS title, COALESCE(p.artist_name, s.artist) AS artist, "
            + "COALESCE(p.poster, s.image) AS cover, p.poster_tag AS posterTag, "
            + "s.start_time AS startTime, v.name AS venueName, v.city AS city, "
            + "COALESCE(s.min_price, p.min_price) AS minPrice, s.status AS status, "
            + "COALESCE(p.is_real_name, s.is_real_name, 1) AS isRealName, "
            + "COALESCE(p.want_count, 0) AS wantCount, s.sell_count AS sellCount "
            + "FROM tb_show s "
            + "INNER JOIN tb_performance p ON p.id = s.performance_id "
            + "INNER JOIN tb_venue v ON v.id = s.venue_id "
            + "WHERE p.status = 2 AND s.status IN (2, 3) "
            + "AND (s.end_time IS NULL OR s.end_time &gt; NOW()) "
            + "<if test='keyword != null and keyword != \"\"'>"
            + " AND (p.title LIKE CONCAT('%',#{keyword},'%') OR p.artist_name LIKE CONCAT('%',#{keyword},'%') "
            + " OR v.name LIKE CONCAT('%',#{keyword},'%')) "
            + "</if>"
            + "<if test='city != null and city != \"\"'> AND v.city = #{city} </if>"
            + "<if test='artistId != null'> AND p.artist_id = #{artistId} </if>"
            + "<if test='timeStart != null'> AND s.start_time &gt;= #{timeStart} </if>"
            + "<if test='timeEnd != null'> AND s.start_time &lt; #{timeEnd} </if>"
            + "<if test='priceMin != null'> AND COALESCE(s.min_price, p.min_price) &gt;= #{priceMin} </if>"
            + "<if test='priceMax != null'> AND COALESCE(s.min_price, p.min_price) &lt;= #{priceMax} </if>"
            + "<if test='hasTicket != null and hasTicket == 1'>"
            + " AND EXISTS (SELECT 1 FROM tb_ticket_type tt WHERE tt.show_id = s.id AND tt.status = 1 AND tt.left_stock &gt; 0)"
            + "</if>"
            + "<choose>"
            + "  <when test='sort == \"hot\"'> ORDER BY s.sell_count DESC, p.want_count DESC, s.start_time ASC </when>"
            + "  <when test='sort == \"update\"'> ORDER BY p.update_time DESC, s.start_time ASC </when>"
            + "  <when test='sort == \"time\"'> ORDER BY s.start_time ASC </when>"
            + "  <otherwise> ORDER BY s.sell_count DESC, p.want_count DESC, s.start_time ASC </otherwise>"
            + "</choose>"
            + " LIMIT #{offset}, #{size}"
            + "</script>")
    List<Map<String, Object>> selectSearchShows(@Param("keyword") String keyword, @Param("city") String city,
                                                @Param("artistId") Long artistId,
                                                @Param("timeStart") LocalDateTime timeStart, @Param("timeEnd") LocalDateTime timeEnd,
                                                @Param("priceMin") java.math.BigDecimal priceMin,
                                                @Param("priceMax") java.math.BigDecimal priceMax,
                                                @Param("hasTicket") Integer hasTicket, @Param("sort") String sort,
                                                @Param("offset") long offset, @Param("size") long size);

    @Select("<script>"
            + "SELECT COUNT(*) FROM tb_artist a WHERE 1=1 "
            + "<if test='keyword != null and keyword != \"\"'> AND a.name LIKE CONCAT('%',#{keyword},'%') </if>"
            + "</script>")
    long countSearchArtists(@Param("keyword") String keyword);

    @Select("<script>"
            + "SELECT a.id, a.name, a.avatar, a.type, a.city, a.follow_count AS followCount "
            + "FROM tb_artist a WHERE 1=1 "
            + "<if test='keyword != null and keyword != \"\"'> AND a.name LIKE CONCAT('%',#{keyword},'%') </if>"
            + " ORDER BY a.follow_count DESC LIMIT #{offset}, #{size}"
            + "</script>")
    List<Map<String, Object>> selectSearchArtists(@Param("keyword") String keyword,
                                                  @Param("offset") long offset, @Param("size") long size);

    @Select("<script>"
            + "SELECT COUNT(*) FROM tb_venue v WHERE 1=1 "
            + "<if test='keyword != null and keyword != \"\"'> AND v.name LIKE CONCAT('%',#{keyword},'%') </if>"
            + "<if test='city != null and city != \"\"'> AND v.city = #{city} </if>"
            + "</script>")
    long countSearchVenues(@Param("keyword") String keyword, @Param("city") String city);

    @Select("<script>"
            + "SELECT v.id, v.name, v.city, v.address "
            + "FROM tb_venue v WHERE 1=1 "
            + "<if test='keyword != null and keyword != \"\"'> AND v.name LIKE CONCAT('%',#{keyword},'%') </if>"
            + "<if test='city != null and city != \"\"'> AND v.city = #{city} </if>"
            + " ORDER BY v.id DESC LIMIT #{offset}, #{size}"
            + "</script>")
    List<Map<String, Object>> selectSearchVenues(@Param("keyword") String keyword, @Param("city") String city,
                                                 @Param("offset") long offset, @Param("size") long size);

    @Select("SELECT p.id AS performanceId, p.title, COALESCE(p.want_count,0) AS wantCount "
            + "FROM tb_performance p WHERE p.status = 2 ORDER BY p.want_count DESC LIMIT 3")
    List<Map<String, Object>> selectWantRankTop3();
}