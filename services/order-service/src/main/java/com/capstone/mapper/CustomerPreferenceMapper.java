package com.capstone.mapper;

import com.capstone.entity.CustomerPreference;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;

@Mapper
public interface CustomerPreferenceMapper {

    @Select("""
            SELECT
                preference_id,
                account_reference,
                theme,
                currency,
                email_notifications_enabled,
                created_at,
                updated_at
            FROM customer_preferences
            WHERE account_reference = #{accountReference}
            """)
    @Results(id = "preferenceMap", value = {
            @Result(property = "preferenceId", column = "preference_id", id = true),
            @Result(property = "accountReference", column = "account_reference"),
            @Result(property = "theme", column = "theme"),
            @Result(property = "currency", column = "currency"),
            @Result(property = "emailNotificationsEnabled", column = "email_notifications_enabled"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "updatedAt", column = "updated_at")
    })
    Optional<CustomerPreference> findByAccountReference(@Param("accountReference") String accountReference);

    @Insert("""
            INSERT INTO customer_preferences
                (account_reference, theme, currency, email_notifications_enabled)
            VALUES
                (#{accountReference}, #{theme}, #{currency}, #{emailNotificationsEnabled})
            ON CONFLICT (account_reference) DO UPDATE
                SET theme = EXCLUDED.theme,
                    currency = EXCLUDED.currency,
                    email_notifications_enabled = EXCLUDED.email_notifications_enabled,
                    updated_at = CURRENT_TIMESTAMP
            """)
    void upsertPreference(
            @Param("accountReference") String accountReference,
            @Param("theme") String theme,
            @Param("currency") String currency,
            @Param("emailNotificationsEnabled") Boolean emailNotificationsEnabled
    );

    @Select("""
            SELECT email_notifications_enabled
            FROM customer_preferences
            WHERE account_reference = #{accountReference}
            """)
    Optional<Boolean> findEmailNotificationsEnabledByAccountReference(
            @Param("accountReference") String accountReference
    );
}
