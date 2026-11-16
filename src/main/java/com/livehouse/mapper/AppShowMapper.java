package com.livehouse.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface AppShowMapper {

    @Select("<script>"
            + "SELECT COUNT(DISTINCT s.id) FROM tb_show s "
            + "INNER JOIN tb_performance p ON p.id = s.performance_id "
            + "INNER JOIN tb_venue v ON v.id = s.venue_id "
            + "WHERE p.status = 2 AND s.status IN (2, 3) "
            + "<if test='city != null and city != \"\"'> AND v.city = #{city} </if>"
            + "<if test='type != null and type != \"\" and type != \"全部\"'> AND p.type = #{type} </if>"
            + "<if test='venueId != null'> AND s.venue_id = #{venueId} </if>"
            + "<if test='artistId != null'> AND p.artist_id = #{artistId} </if>"
            + "<if test='date != null and date != \"\"'> AND DATE(s.start_time) = #{date} </if>"
            + "</script>")
    long countShowList(@Param("city") String city, @Param("type") String type,
                       @Param("date") String date, @Param("venueId") Long venueId,
                       @Param("artistId") Long artistId);

    @Select("<script>"
            + "SELECT s.id AS showId, p.id AS performanceId, "
            + "COALESCE(p.title, s.title) AS title, COALESCE(p.artist_name, s.artist) AS artist, "
            + "COALESCE(p.poster, s.image) AS cover, p.poster_tag AS posterTag, "
            + "s.start_time AS startTime, v.name AS venueName, v.city AS city, "
            + "COALESCE(s.min_price, p.min_price) AS minPrice, s.status AS status, "
            + "COALESCE(p.is_real_name, s.is_real_name, 1) AS isRealName, "
            + "COALESCE(p.want_count, 0) AS wantCount "
            + "FROM tb_show s "
            + "INNER JOIN tb_performance p ON p.id = s.performance_id "
            + "INNER JOIN tb_venue v ON v.id = s.venue_id "
            + "WHERE p.status = 2 AND s.status IN (2, 3) "
            + "<if test='city != null and city != \"\"'> AND v.city = #{city} </if>"
            + "<if test='type != null and type != \"\" and type != \"全部\"'> AND p.type = #{type} </if>"
            + "<if test='venueId != null'> AND s.venue_id = #{venueId} </if>"
            + "<if test='artistId != null'> AND p.artist_id = #{artistId} </if>"
            + "<if test='date != null and date != \"\"'> AND DATE(s.start_time) = #{date} </if>"
            + "<choose>"
            + "  <when test='sort == \"hot\" or sort == \"want\"'> ORDER BY p.want_count DESC, s.start_time ASC </when>"
            + "  <when test='sort == \"price\"'> ORDER BY minPrice ASC, s.start_time ASC </when>"
            + "  <otherwise> ORDER BY s.start_time ASC </otherwise>"
            + "</choose>"
            + " LIMIT #{offset}, #{size}"
            + "</script>")
    List<Map<String, Object>> selectShowList(@Param("city") String city, @Param("type") String type,
                                              @Param("date") String date, @Param("venueId") Long venueId,
                                              @Param("artistId") Long artistId, @Param("sort") String sort,
                                              @Param("offset") long offset, @Param("size") long size);

    @Select("SELECT DISTINCT v.city FROM tb_venue v WHERE v.city IS NOT NULL AND v.city != '' ORDER BY v.city")
    List<String> selectDistinctCities();

    @Select("SELECT DISTINCT p.type FROM tb_performance p WHERE p.status = 2 AND p.type IS NOT NULL ORDER BY p.type")
    List<String> selectDistinctTypes();
}