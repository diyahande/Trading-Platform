import { ApiProperty } from '@nestjs/swagger';

export class ApproveSignupRequestResponseDto {
  @ApiProperty({ format: 'uuid' })
  requestId!: string;

  @ApiProperty({ format: 'uuid' })
  authUserId!: string;

  @ApiProperty({ format: 'uuid' })
  accountId!: string;

  @ApiProperty()
  emailSent!: boolean;
}
