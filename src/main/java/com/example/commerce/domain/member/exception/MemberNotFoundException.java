package com.example.commerce.domain.member.exception;

import com.example.commerce.domain.common.exception.BusinessException;
import com.example.commerce.domain.common.exception.ErrorCode;

public class MemberNotFoundException extends BusinessException {

    public MemberNotFoundException(Long memberId) {
        super(ErrorCode.MEMBER_NOT_FOUND);
    }
}
