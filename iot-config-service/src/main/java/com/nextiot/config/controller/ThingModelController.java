package com.nextiot.config.controller;

import com.nextiot.common.entity.ThingModel;
import com.nextiot.common.entity.ThingProperty;
import com.nextiot.config.mapper.ThingModelMapper;
import com.nextiot.config.mapper.ThingPropertyMapper;
import com.nextiot.common.dto.Result;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 物模型和点位管理控制器
 */
@RestController
@RequestMapping("/api/model")
@CrossOrigin(origins = "*")
public class ThingModelController {

    @Resource
    private ThingModelMapper thingModelMapper;

    @Resource
    private ThingPropertyMapper thingPropertyMapper;

    /**
     * 查询所有物模型
     */
    @GetMapping("/list")
    public Result<List<ThingModel>> listModels() {
        List<ThingModel> models = thingModelMapper.selectList(null);
        return Result.success(models);
    }

    /**
     * 创建物模型
     */
    @PostMapping("/create")
    public Result<ThingModel> createModel(@RequestBody ThingModel model) {
        try {
            model.setCreatedAt(System.currentTimeMillis());
            model.setUpdatedAt(System.currentTimeMillis());
            thingModelMapper.insert(model);
            return Result.success(model);
        } catch (Exception e) {
            return Result.fail("创建物模型失败：" + e.getMessage());
        }
    }

    /**
     * 更新物模型
     */
    @PostMapping("/update")
    public Result<ThingModel> updateModel(@RequestBody ThingModel model) {
        try {
            model.setUpdatedAt(System.currentTimeMillis());
            thingModelMapper.updateById(model);
            return Result.success(model);
        } catch (Exception e) {
            return Result.fail("更新物模型失败：" + e.getMessage());
        }
    }

    /**
     * 删除物模型
     */
    @DeleteMapping("/{modelCode}")
    public Result<Void> deleteModel(@PathVariable String modelCode) {
        try {
            ThingModel model = thingModelMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ThingModel>()
                            .eq(ThingModel::getModelCode, modelCode));
            if (model != null) {
                thingModelMapper.deleteById(model.getId());
            }
            return Result.success();
        } catch (Exception e) {
            return Result.fail("删除物模型失败：" + e.getMessage());
        }
    }

    /**
     * 根据物模型查询点位列表
     */
    @GetMapping("/{modelCode}/properties")
    public Result<List<ThingProperty>> getProperties(@PathVariable String modelCode) {
        List<ThingProperty> properties = thingPropertyMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ThingProperty>()
                        .eq(ThingProperty::getModelCode, modelCode));
        return Result.success(properties);
    }

    /**
     * 创建点位
     */
    @PostMapping("/property/create")
    public Result<ThingProperty> createProperty(@RequestBody ThingProperty property) {
        try {
            property.setCreatedAt(System.currentTimeMillis());
            property.setUpdatedAt(System.currentTimeMillis());
            thingPropertyMapper.insert(property);
            return Result.success(property);
        } catch (Exception e) {
            return Result.fail("创建点位失败：" + e.getMessage());
        }
    }

    /**
     * 更新点位
     */
    @PostMapping("/property/update")
    public Result<ThingProperty> updateProperty(@RequestBody ThingProperty property) {
        try {
            property.setUpdatedAt(System.currentTimeMillis());
            thingPropertyMapper.updateById(property);
            return Result.success(property);
        } catch (Exception e) {
            return Result.fail("更新点位失败：" + e.getMessage());
        }
    }

    /**
     * 删除点位
     */
    @DeleteMapping("/property/{id}")
    public Result<Void> deleteProperty(@PathVariable Long id) {
        try {
            thingPropertyMapper.deleteById(id);
            return Result.success();
        } catch (Exception e) {
            return Result.fail("删除点位失败：" + e.getMessage());
        }
    }
}