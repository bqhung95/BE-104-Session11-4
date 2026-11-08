package com.example.pharmacy.service;

import com.example.pharmacy.entity.Medicine;
import com.example.pharmacy.repository.MedicineRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class MedicineService {

    private final MedicineRepository medicineRepository;
    private final RedissonClient redissonClient;

    public String sellMedicine(Long id) {
        String lockKey = "lock:medicine:" + id;
        RLock lock = redissonClient.getLock(lockKey);

        boolean locked = false;
        try {
            // ✅ tryLock(waitTime=3s, leaseTime=5s, unit=SECONDS)
            locked = lock.tryLock(3, 5, TimeUnit.SECONDS);

            if (!locked) {
                return "Hệ thống đang bận, vui lòng thử lại sau!";
            }

            // ====== CRITICAL SECTION ======
            // Chỉ 1 thread được vào đây tại 1 thời điểm với cùng medicine id
            Medicine medicine = medicineRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Thuốc không tồn tại: " + id));

            if (medicine.getQuantity() <= 0) {
                return "Sản phẩm đã hết hàng!";
            }

            // Trừ kho
            medicine.setQuantity(medicine.getQuantity() - 1);
            if (medicine.getQuantity() == 0) {
                medicine.setStatusStock("Hết hàng");
            }
            medicineRepository.save(medicine);

            return "Thanh toán thành công thuốc: " + medicine.getMedicineName();
            // ====== END CRITICAL SECTION ======

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Thread bị ngắt khi chờ lock";
        } finally {
            // ✅ Chỉ unlock nếu lock do chính thread hiện tại giữ
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
