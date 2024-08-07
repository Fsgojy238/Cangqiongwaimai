package com.sky.service.impl;


import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.dto.SetmealDTO;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.entity.SetmealDish;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.exception.SetmealEnableFailedException;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealDishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.result.PageResult;
import com.sky.service.SetmealService;
import com.sky.vo.SetmealVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 套餐业务实现
 */
@Service
public class SetmealServiceImpl implements SetmealService {

    @Autowired
    private SetmealMapper setmealMapper;
    @Autowired
    private SetmealDishMapper setmealDishMapper;
    @Autowired
    private DishMapper dishMapper;


    /**
     * 新增菜品，同时需要保存套餐和和菜品的关联关系
     *
     * @param setmealDTO
     */
    @Transactional
    public void saveWithDish(SetmealDTO setmealDTO) {
        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO, setmeal);

        //向套餐表插入数据
        setmealMapper.insert(setmeal);

        //获取生成的套餐id, 对setmeal_dish表进行操作
        Long setmealId = setmeal.getId();

        List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();
        for (SetmealDish setmealDish : setmealDishes) {
            setmealDish.setSetmealId(setmealId);
        }

        //保存套餐和菜品的关联关系
        setmealDishMapper.insertBatch(setmealDishes);
    }

    /**
     * 套餐分页查询
     *
     * @param setmealPageQueryDTO
     * @return
     */
    public PageResult pageQuery(SetmealPageQueryDTO setmealPageQueryDTO) {
        PageHelper.startPage(setmealPageQueryDTO.getPage(), setmealPageQueryDTO.getPageSize());
        Page<SetmealVO> page = setmealMapper.pageQuery(setmealPageQueryDTO);
        return new PageResult(page.getTotal(), page.getResult());
    }

    /**
     * 批量删除套餐
     *
     * @param ids
     */
    public void deleteBatch(List<Long> ids) {
        //先检查套餐是否起售中，起售中的套餐不能删除
        for (Long id : ids) {
            Setmeal setmeal = setmealMapper.selectById(id);
            if (Objects.equals(setmeal.getStatus(), StatusConstant.ENABLE)) {
                throw new DeletionNotAllowedException(MessageConstant.SETMEAL_ON_SALE);
            }
        }

        //删除套餐表中的数据
        setmealMapper.deleteByIds(ids);

        //删除套餐菜品关系表中的数据
        setmealMapper.deleteBySetmealIds(ids);
    }

    /**
     * 修改套餐
     *
     * @param setmealDTO
     */
    @Transactional
    public void update(SetmealDTO setmealDTO) {
        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO, setmeal);
        //修改套餐
        setmealMapper.update(setmeal);

        //如果要更改菜品信息，先删除再重新添加
        Long setmealId = setmeal.getId();
        List<Long> setmealIds = new ArrayList<>();
        setmealIds.add(setmealId);
        setmealMapper.deleteByIds(setmealIds);

        List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();
        for (SetmealDish setmealDish : setmealDishes) {
            setmealDish.setSetmealId(setmealId);
        }
        //重新添加菜品信息
        setmealDishMapper.insertBatch(setmealDishes);
    }

    /**
     * 根据id查询套餐
     *
     * @param id
     * @return
     */
    public SetmealVO selectSetmealById(Long id) {
        //先查出setmeal信息，然后查出setmealDishes信息
        Setmeal setmeal = setmealMapper.selectById(id);
        List<SetmealDish> list = setmealDishMapper.selectBySetmealId(id);

        SetmealVO setmealVO = new SetmealVO();
        BeanUtils.copyProperties(setmeal, setmealVO);
        setmealVO.setSetmealDishes(list);

        return setmealVO;
    }

    /**
     * 套餐起售停售
     * @param status
     */
    public void startOrStop(Integer status, Long setmealId) {
        //起售套餐时，如果套餐内有未起售的菜品，需要提示无法起售该套餐
        if(Objects.equals(status, StatusConstant.ENABLE)){
            List<Dish> dishList = dishMapper.getDishBySetmealId(setmealId);
            for (Dish dish : dishList) {
                if(dish.getStatus()==StatusConstant.DISABLE){
                    throw new SetmealEnableFailedException(MessageConstant.SETMEAL_ENABLE_FAILED);
                }
            }
        }

        Setmeal setmeal = Setmeal.builder()
                .status(status)
                .id(setmealId)
                .build();
        setmealMapper.update(setmeal);
    }
}
