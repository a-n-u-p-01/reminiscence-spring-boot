package com.anupam.reminiscence.service.impl;

import com.anupam.reminiscence.entity.AppInfo;
import com.anupam.reminiscence.repo.AppInfoRepo;
import com.anupam.reminiscence.service.AppInfoService;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AppInfoServiceImpl implements AppInfoService {
    private final AppInfoRepo appInfoRepo;

    @Override
    public String getAppVersion() {
        AppInfo appInfo = appInfoRepo.findByInfoName("app_version").orElseThrow(()->new RuntimeException("Something went wrong"));
        return appInfo.getInfoValue();
    }
}
