package com.sample.domain.dao;

import java.util.stream.Collector;

import org.seasar.doma.Dao;
import org.seasar.doma.Select;
import org.seasar.doma.SelectType;
import org.seasar.doma.boot.ConfigAutowireable;

import com.sample.domain.dto.Permission;
import com.sample.domain.dto.Staff;
import com.sample.domain.dto.common.ID;

@ConfigAutowireable
@Dao
public interface StaffPermissionDao {

    /**
     * 担当者権限を取得します。
     *
     * @return
     */
    @Select(strategy = SelectType.COLLECT)
    <R> R selectByStaffId(ID<Staff> id, final Collector<Permission, ?, R> collector);
}