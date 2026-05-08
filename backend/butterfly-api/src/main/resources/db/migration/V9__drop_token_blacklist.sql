-- token_blacklist 테이블 제거.
-- Access Token 블랙리스트는 Redis(auth:blacklist:{jti})로 관리하므로 DB 테이블 불필요.
-- 관련 엔티티/레포지토리(TokenBlacklist, TokenBlacklistRepository)도 동시에 삭제.
DROP TABLE IF EXISTS token_blacklist;
