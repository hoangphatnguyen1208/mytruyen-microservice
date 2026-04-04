package online.mytruyen.authservice.client;

import feign.codec.ErrorDecoder;
import online.mytruyen.authservice.exception.UnauthorizedException;

public class UserClientErrorEncoder extends ErrorDecoder.Default {
    @Override
    public Exception decode(String methodKey, feign.Response response) {
        return switch (response.status()) {
            case 404 -> new UnauthorizedException("Invalid username or password");
            default -> super.decode(methodKey, response);
        };
    }
}
