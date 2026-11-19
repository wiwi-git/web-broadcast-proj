package com.webbroadcast.simulcast;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface SimulcastSettingRepository extends JpaRepository<SimulcastSetting, UUID> {
}
