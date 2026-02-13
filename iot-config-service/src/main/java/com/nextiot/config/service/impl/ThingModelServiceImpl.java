package com.nextiot.config.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nextiot.common.entity.ThingModel;
import com.nextiot.config.mapper.ThingModelMapper;
import com.nextiot.config.service.ThingModelService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 物模型管理服务实现
 */
@Service
public class ThingModelServiceImpl extends ServiceImpl<ThingModelMapper, ThingModel> implements ThingModelService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ThingModel createThingModel(ThingModel model) {
        // 校验 modelCode 唯一性
        ThingModel existing = getOne(new LambdaQueryWrapper<ThingModel>()
                .eq(ThingModel::getModelCode, model.getModelCode()));
        if (existing != null) {
            throw new RuntimeException("物模型编码已存在: " + model.getModelCode());
        }

        model.setCreatedAt(System.currentTimeMillis());
        model.setUpdatedAt(System.currentTimeMillis());
        save(model);
        return model;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ThingModel updateThingModel(ThingModel model) {
        model.setUpdatedAt(System.currentTimeMillis());
        updateById(model);
        return model;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteThingModel(String modelCode) {
        ThingModel model = getOne(new LambdaQueryWrapper<ThingModel>()
                .eq(ThingModel::getModelCode, modelCode));
        if (model != null) {
            removeById(model.getId());
        }
    }

    @Override
    public List<ThingModel> getAllModels() {
        return list();
    }
}
