package com.b101.butterfly.auth.port;

import java.util.Optional;

public interface CompanyAuthPort {

    Optional<Long> findProjectIdByCompanyAuthNo(String companyAuthNo);
}
