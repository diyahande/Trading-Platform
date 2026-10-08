# API Client - Auto-Generated TypeScript Angular Client

This directory contains auto-generated TypeScript/Angular API client code generated from the trading platform's OpenAPI specifications. The generated code is maintained automatically and should not be manually edited.

## Overview

The API client provides type-safe Angular services and models for communicating with multiple backend services:
- **Trade API** - Order execution, portfolio, and trading functionality
- **Auth Service** - Authentication and authorization services

## Prerequisites

- **Node.js** and npm installed
- **Trade API** service running (port 8083)
- **Auth Service** running (port 3000)
- OpenAPI/Swagger endpoints available on both services

## Dependencies

### Install OpenAPI Generator CLI

Add OpenAPI Generator CLI as a development dependency:

```bash
npm install @openapitools/openapi-generator-cli --save-dev
```

This tool automatically generates TypeScript/Angular client code from OpenAPI specifications.

## API Code Generation

### Trade API Client Generation

Generate the TypeScript Angular client for the Trade API:

```bash
npx openapi-generator-cli generate \
  -i http://localhost:8083/v3/api-docs \
  -g typescript-angular \
  -o src/app/generated/api-client
```

**Parameters:**
- `-i`: OpenAPI spec URL from Trade API (port 8083)
- `-g`: Generator type (typescript-angular)
- `-o`: Output directory

### Auth Service Client Generation

For auth-stub or alternative Auth Service implementation, use:

```bash
npx openapi-generator-cli generate \
  -i http://localhost:3000/docs/json \
  -g typescript-angular \
  -o src/app/generated/api-client
```

**Parameters:**
- `-i`: OpenAPI/documentation JSON from Auth Service (port 3000)
- `-g`: Generator type (typescript-angular)
- `-o`: Output directory

## Backend Configuration

### Trade API - OpenAPI Exposure

Ensure the Trade API exposes the OpenAPI specification at `/v3/api-docs`:

```yaml
# application.yaml or application.properties
springdoc:
  api-docs:
    path: /v3/api-docs
  swagger-ui:
    path: /swagger-ui.html
```

### Auth Service - Security Configuration

To allow public access to OpenAPI documentation endpoints in Auth Service, add this Spring Security configuration:

```java
@Bean
public WebSecurityCustomizer webSecurityCustomizer() {
    return (web) -> web.ignoring().requestMatchers(
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    );
}
```

This configuration allows:
- `/v3/api-docs/**` - OpenAPI specification endpoints
- `/swagger-ui/**` - Swagger UI resources
- `/swagger-ui.html` - Swagger UI interface

## Generated Code Structure

The generated client includes:

- **Models**: TypeScript interfaces for request/response data structures
- **Services**: Angular services with methods for each API endpoint
- **Configuration**: API base URL and HTTP client setup
- **Enums**: Enumerated types used in API contracts

## Using the Generated Client

### Import Services

Import generated services into your Angular components:

```typescript
import { OrderService, PortfolioService } from './generated/api-client';

@Component({
  selector: 'app-orders',
  templateUrl: './orders.component.html'
})
export class OrdersComponent {
  constructor(private orderService: OrderService) {}

  loadOrders() {
    this.orderService.getOrders().subscribe(
      (orders) => console.log(orders),
      (error) => console.error('Failed to load orders', error)
    );
  }
}
```

### Configure API Base URL

Set the API base URL in your Angular module or environment configuration:

```typescript
// environment.ts
export const environment = {
  tradeApiUrl: 'http://localhost:8083',
  authServiceUrl: 'http://localhost:3000'
};
```

## Regeneration Workflow

When backend APIs are updated:

1. Ensure both services are running with updated OpenAPI documentation
2. Run the appropriate generator command (Trade API or Auth Service)
3. Review generated code changes
4. Update consuming components if API contracts changed
5. Run tests to verify integration
6. Commit changes to version control

## Important Notes

- ⚠️ **Do not manually edit** files in this directory - they will be overwritten during regeneration
- Keep backend services running during code generation
- Ensure OpenAPI endpoints are accessible from your development machine
- Generated code should be committed to repository for team consistency
- Different services may have different API endpoints and port numbers

## Troubleshooting

| Issue | Solution |
|-------|----------|
| Connection refused to Trade API (8083) | Start Trade API service: `mvn spring-boot:run` in trade-api/ |
| Connection refused to Auth Service (3000) | Start Auth Service or auth-stub |
| Missing OpenAPI endpoint | Verify Springdoc dependency and configuration |
| Type mismatches after generation | Clear node_modules and regenerate: `npm install && npm run generate` |
| CORS errors | Check backend CORS configuration and allowed origins |

## Service Details

### Trade API (Port 8083)
- OpenAPI Spec: `http://localhost:8083/v3/api-docs`
- Swagger UI: `http://localhost:8083/swagger-ui.html`
- Function: Order management, portfolio tracking, execution services

### Auth Service (Port 3000)
- Docs: `http://localhost:3000/docs/json`
- Function: User authentication, token management, authorization