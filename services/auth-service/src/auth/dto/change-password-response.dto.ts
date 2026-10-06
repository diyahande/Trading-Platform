import { ApiProperty } from '@nestjs/swagger';

export class ChangePasswordResponseDto {
  @ApiProperty({
    example: 'Password changed successfully. Please sign in again.',
  })
  message!: string;
}
