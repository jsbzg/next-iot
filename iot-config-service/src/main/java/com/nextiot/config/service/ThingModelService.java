package com.nextiot.config.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.nextiot.common.entity.ThingModel;

import java.util.List;

/**
 * 物模型管理服务接口
 */
public interface ThingModelService extends IService<ThingModel> {

    /**
     * 创建物模型
     */
    ThingModel createThingModel(ThingModel model);

    /**
     * 更新物模型
     */
    ThingModel updateThingModel(ThingModel model);

    /**
     * 删除物模型
     */
    void deleteThingModel(String modelCode);

    /**
     * 获取所有物模型
     */
    List<ThingModel> getAllModels();
}