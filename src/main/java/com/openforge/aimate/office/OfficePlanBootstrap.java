package com.openforge.aimate.office;

import com.openforge.aimate.domain.OfficePlan;
import com.openforge.aimate.repository.OfficePlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(45)
@RequiredArgsConstructor
public class OfficePlanBootstrap implements ApplicationRunner {

    private final OfficePlanRepository planRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seed("OFFICE_S", "小型办公室", 20, "S5.MEDIUM2", "2 核", "2 GB", 50,
                500, 8000, 72000, "适合轻量脚本与单 Agent 常驻", 10);
        seed("OFFICE_M", "标准办公室", 50, "S5.LARGE2", "2 核", "4 GB", 80,
                1200, 18000, 160000, "可部署轻量服务、多 Agent 共用", 20);
        seed("OFFICE_L", "大型办公室", 100, "S5.2XLARGE4", "4 核", "8 GB", 100,
                2800, 42000, 380000, "适合多服务部署与重负载工具链", 30);
    }

    private void seed(
            String code, String name, int area, String instanceType,
            String cpu, String mem, int disk,
            long day, long month, long year, String desc, int sort
    ) {
        if (planRepository.findByCodeIgnoreCase(code).isPresent()) return;
        OfficePlan p = new OfficePlan();
        p.setCode(code);
        p.setName(name);
        p.setAreaSqm(area);
        p.setInstanceType(instanceType);
        p.setCpuDesc(cpu);
        p.setMemDesc(mem);
        p.setDiskGb(disk);
        p.setPriceDayPoints(day);
        p.setPriceMonthPoints(month);
        p.setPriceYearPoints(year);
        p.setDescription(desc);
        p.setEnabled(true);
        p.setSortOrder(sort);
        planRepository.save(p);
    }
}
