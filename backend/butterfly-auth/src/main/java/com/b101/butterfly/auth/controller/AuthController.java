package com.b101.butterfly.auth.controller;

import com.b101.butterfly.auth.dto.request.EmailSendCodeRequest;
import com.b101.butterfly.auth.dto.request.EmailVerifyRequest;
import com.b101.butterfly.auth.dto.request.LoginRequest;
import com.b101.butterfly.auth.dto.request.PasswordChangeRequest;
import com.b101.butterfly.auth.dto.request.PhoneChangeRequest;
import com.b101.butterfly.auth.dto.request.SetOwnerPasswordRequest;
import com.b101.butterfly.auth.dto.request.SignupRequest;
import com.b101.butterfly.auth.dto.request.TokenRefreshRequest;
import com.b101.butterfly.auth.dto.request.WithdrawRequest;
import com.b101.butterfly.auth.dto.response.LoginResponse;
import com.b101.butterfly.auth.dto.response.MemberInfoResponse;
import com.b101.butterfly.auth.dto.response.SignupResponse;
import com.b101.butterfly.auth.security.CustomUserDetails;
import com.b101.butterfly.auth.service.AuthService;
import com.b101.butterfly.auth.service.EmailVerificationService;
import com.b101.butterfly.auth.service.MemberService;
import com.b101.butterfly.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@SuppressWarnings("null")
public class AuthController {

    private final EmailVerificationService emailVerificationService;
    private final AuthService authService;
    private final MemberService memberService;

    // 이메일 인증 코드 발송 (공개)
    @PostMapping("/email/send-code")
    public ResponseEntity<ApiResponse<Void>> sendCode(
            @Valid @RequestBody EmailSendCodeRequest request) {
        emailVerificationService.sendCode(request.email());
        return ResponseEntity.ok(ApiResponse.ok());
    }

    // 이메일 인증 코드 확인 (공개)
    @PostMapping("/email/verify")
    public ResponseEntity<ApiResponse<Void>> verifyCode(
            @Valid @RequestBody EmailVerifyRequest request) {
        emailVerificationService.verifyCode(request.email(), request.code());
        return ResponseEntity.ok(ApiResponse.ok());
    }

    // 직원 회원가입 (공개)
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignupResponse>> signup(
            @Valid @RequestBody SignupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(authService.signup(request)));
    }

    // 로그인 (공개)
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(authService.login(request)));
    }

    // 로그아웃 (인증 필요)
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        String accessToken = (authorizationHeader != null && authorizationHeader.startsWith("Bearer "))
                ? authorizationHeader.substring(7) : null;
        authService.logout(userDetails.getMemberId(), accessToken);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    // Refresh Token 재발급 (공개)
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<LoginResponse>> refresh(
            @Valid @RequestBody TokenRefreshRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(authService.refreshToken(request.refreshToken())));
    }

    // 내 정보 조회 (인증 필요)
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MemberInfoResponse>> getMyInfo(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.ok(memberService.getMyInfo(userDetails.getMemberId())));
    }

    // 비밀번호 변경 (인증 필요)
    @PutMapping("/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody PasswordChangeRequest request) {
        memberService.changePassword(userDetails.getMemberId(), request);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    // 전화번호 변경 (인증 필요)
    @PutMapping("/phone")
    public ResponseEntity<ApiResponse<Void>> changePhone(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody PhoneChangeRequest request) {
        memberService.changePhone(userDetails.getMemberId(), request);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    // 회원 탈퇴 (인증 필요)
    @DeleteMapping("/withdraw")
    public ResponseEntity<ApiResponse<Void>> withdraw(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody WithdrawRequest request) {
        memberService.withdraw(userDetails.getMemberId(), request);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    // Owner 초대 링크로 비밀번호 최초 설정 (공개)
    @PostMapping("/owner/set-password")
    public ResponseEntity<ApiResponse<Void>> setOwnerPassword(
            @Valid @RequestBody SetOwnerPasswordRequest request) {
        authService.setOwnerPassword(request);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
