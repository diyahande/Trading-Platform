import { ApiProperty } from '@nestjs/swagger';

export class RejectSignupRequestResponseDto {
  @ApiProperty({ format: 'uuid' })
  requestId!: string;

  @ApiProperty()
  emailSent!: boolean;
}
